package bd.shromikai.analysis;

import bd.shromikai.analysis.CandidateSet.Candidate;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/**
 * Step {@code CORRECT}, the corrective loop of agentic RAG (architecture §7.5, FR-46): for an LLM
 * finding whose citation was not verified, rewrite the query from the finding, retrieve again, add
 * the new sections to the candidates (still capped at 40), ask {@code risk-citation-repair} once
 * and verify the new citation as attempt 2. There is no attempt 3. A finding still without a
 * verified citation stays {@code UNCERTAIN}. Repairs for different findings run in parallel.
 */
@Component
public class CorrectiveLoop {

  static final String PROMPT = "risk-citation-repair";
  static final String ENUM_PATH = "$.citations[*].section_key";

  private final CandidateSource retrieval;
  private final CandidateRetriever retriever;
  private final FindingVerifier findingVerifier;
  private final CitationVerifier verifier;
  private final VerifyProperties properties;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public CorrectiveLoop(
      CandidateSource retrieval,
      CandidateRetriever retriever,
      FindingVerifier findingVerifier,
      CitationVerifier verifier,
      VerifyProperties properties) {
    this.retrieval = retrieval;
    this.retriever = retriever;
    this.findingVerifier = findingVerifier;
    this.verifier = verifier;
    this.properties = properties;
  }

  /** Content-free record of what the loop did for one finding. */
  public record Correction(
      String findingId, boolean repaired, int newSections, boolean abstained) {}

  public record Round(List<Finding> findings, List<Correction> corrections) {}

  /** Whether any finding needs the loop. */
  public boolean needed(List<Finding> findings) {
    return properties.correctiveAttempts() > 0 && findings.stream().anyMatch(this::eligible);
  }

  private boolean eligible(Finding finding) {
    return finding.verification() == FindingVerification.UNCERTAIN
        && finding.source() == FindingSource.LLM_ANALYSIS;
  }

  public Round correct(
      ModelSession session,
      AnalysisRequest request,
      List<Finding> findings,
      List<Clause> clauses,
      CandidateSet candidates,
      VerificationContext context) {
    Map<String, Clause> byId = new LinkedHashMap<>();
    clauses.forEach(c -> byId.put(c.id(), c));
    List<Future<Object[]>> futures = new ArrayList<>();
    for (Finding finding : findings) {
      if (!eligible(finding) || properties.correctiveAttempts() < 1) {
        futures.add(null);
        continue;
      }
      futures.add(
          executor.submit(() -> correctOne(session, request, finding, byId, candidates, context)));
    }
    List<Finding> out = new ArrayList<>();
    List<Correction> corrections = new ArrayList<>();
    try {
      for (int i = 0; i < findings.size(); i++) {
        Future<Object[]> future = futures.get(i);
        if (future == null) {
          out.add(findings.get(i));
          continue;
        }
        Object[] done = future.get();
        out.add((Finding) done[0]);
        corrections.add((Correction) done[1]);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.stream().filter(java.util.Objects::nonNull).forEach(f -> f.cancel(true));
      throw new IllegalStateException("correction interrupted", e);
    } catch (ExecutionException e) {
      futures.stream().filter(java.util.Objects::nonNull).forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("correction failed", e.getCause());
    }
    return new Round(out, corrections);
  }

  private Object[] correctOne(
      ModelSession session,
      AnalysisRequest request,
      Finding finding,
      Map<String, Clause> clauses,
      CandidateSet candidates,
      VerificationContext context) {
    String query = finding.title().reveal() + ". " + finding.explanation().reveal();
    List<String> added =
        retriever.extend(
            candidates, retrieval.search(query, finding.clauseType(), session.egress()).results());
    Map<String, Object> variables = new LinkedHashMap<>();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("title", finding.title().reveal());
    summary.put("kind", finding.kind().name());
    summary.put("severity", finding.severity().name());
    summary.put("explanation", finding.explanation().reveal());
    summary.put(
        "failedClaim",
        finding.claims().isEmpty() ? "none" : finding.claims().getFirst().claim().reveal());
    variables.put("finding", summary);
    variables.put("reason_codes", finding.reasonCodes());
    List<Candidate> all = candidates.all();
    variables.put("candidate_sections", all);
    List<Map<String, String>> clauseTexts = new ArrayList<>();
    for (String id : finding.clauseIds()) {
      Clause clause = clauses.get(id);
      if (clause != null) {
        clauseTexts.add(Map.of("id", id, "text", clause.text().reveal()));
      }
    }
    variables.put("clauses", clauseTexts);
    List<String> keys = all.stream().map(Candidate::sectionKey).toList();
    JsonNode output = null;
    try {
      ModelGateway.Result result =
          session.call(Stage.CORRECT, PROMPT, variables, Map.of(ENUM_PATH, keys));
      output = result.output();
    } catch (ModelOutputInvalidException e) {
      // Treated as ABSTAIN (architecture §5.4): the finding stays UNCERTAIN.
    }
    JsonNode citations = output == null ? null : output.path("citations");
    if (citations == null || !"OK".equals(output.path("status").asText()) || citations.isEmpty()) {
      return new Object[] {finding, new Correction(finding.id(), false, added.size(), true)};
    }
    JsonNode c = citations.get(0);
    ClaimedCitation repaired =
        ClaimedCitation.llm(
            c.path("section_key").asText(),
            c.path("quote").asText(),
            SensitiveText.of(c.path("claim").asText()),
            request.outputLanguage());
    VerificationContext second = context.forAttempt(2, candidates.keys());
    Verdict verdict = verifier.verify(repaired, second);
    Finding updated;
    if (verdict.verified()) {
      updated = finding.verified(List.of(verdict.citation()));
    } else {
      List<String> reasons = new ArrayList<>(finding.reasonCodes());
      verdict.result().reasonCodes().forEach(r -> reasons.add(r.name() + "@2"));
      updated = finding.withClaims(List.of(repaired)).uncertain(reasons);
    }
    return new Object[] {
      updated, new Correction(finding.id(), verdict.verified(), added.size(), false)
    };
  }
}
