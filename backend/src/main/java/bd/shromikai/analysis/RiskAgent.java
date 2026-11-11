package bd.shromikai.analysis;

import bd.shromikai.analysis.CandidateSet.Candidate;
import bd.shromikai.rules.ClauseFact;
import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.verify.ClaimedCitation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/**
 * The risk agent (FR-37, architecture §8.5): opposing counsel for the user's side, run on up to
 * four batches of contiguous clauses in parallel, each with an outline of the whole contract. The
 * schema restricts every cited section key to the batch's candidates, so a model cannot cite
 * outside what retrieval found. What it returns are findings with <em>claimed</em> citations only;
 * the verifier decides what a user sees (R-01). A call that stops at its output cap, or returns
 * invalid output after the gateway's repair, is retried once as two half batches.
 */
@Component
public final class RiskAgent {

  static final String PROMPT = "risk-analysis";
  static final String ENUM_PATH = "$.findings[*].citations[*].section_key";
  static final int MAX_BATCHES = 4;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /** What one clause looks like to the agent. */
  public record ClauseView(Clause clause, List<ClauseFact> facts) {}

  /** The findings and how many model calls and batch splits it took. */
  public record Result(List<Finding> findings, int batches, int splits) {}

  public Result run(
      ModelSession session,
      AnalysisRequest request,
      List<ClauseView> clauses,
      CandidateSet candidates,
      List<Map<String, Object>> ruleFindings,
      List<Map<String, Object>> expectedTopicsMissing) {
    List<Map<String, Object>> outline = outline(clauses);
    List<List<ClauseView>> batches = batches(clauses, MAX_BATCHES);
    List<Future<List<Finding>>> futures = new ArrayList<>();
    int[] splits = {0};
    for (List<ClauseView> batch : batches) {
      futures.add(
          executor.submit(
              () ->
                  runBatch(
                      session,
                      request,
                      batch,
                      outline,
                      candidates,
                      ruleFindings,
                      expectedTopicsMissing,
                      splits,
                      true)));
    }
    List<Finding> all = new ArrayList<>();
    try {
      for (Future<List<Finding>> future : futures) {
        all.addAll(future.get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.forEach(f -> f.cancel(true));
      throw new IllegalStateException("risk analysis interrupted", e);
    } catch (ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("risk analysis failed", e.getCause());
    }
    return new Result(all, batches.size(), splits[0]);
  }

  /** One call of the risk agent: its variables, the clauses it covers and the keys it may cite. */
  public record Batch(
      List<String> clauseIds, Map<String, Object> variables, List<String> candidateKeys) {

    /** The per-request enums of the gateway call (architecture §8.3). */
    public Map<String, List<String>> enums() {
      return Map.of(ENUM_PATH, candidateKeys);
    }
  }

  /** The batches of {@link #run}, without calling a model (used by the live one-batch check). */
  public List<Batch> plan(
      AnalysisRequest request,
      List<ClauseView> clauses,
      CandidateSet candidates,
      List<Map<String, Object>> ruleFindings,
      List<Map<String, Object>> expectedTopicsMissing) {
    List<Map<String, Object>> outline = outline(clauses);
    List<Batch> out = new ArrayList<>();
    for (List<ClauseView> batch : batches(clauses, MAX_BATCHES)) {
      out.add(batch(request, batch, outline, candidates, ruleFindings, expectedTopicsMissing));
    }
    return out;
  }

  private static Batch batch(
      AnalysisRequest request,
      List<ClauseView> batch,
      List<Map<String, Object>> outline,
      CandidateSet candidates,
      List<Map<String, Object>> ruleFindings,
      List<Map<String, Object>> expectedTopicsMissing) {
    List<String> ids = batch.stream().map(v -> v.clause().id()).toList();
    List<Candidate> forBatch = candidates.forClauses(ids);
    if (forBatch.isEmpty()) {
      forBatch = candidates.all().stream().limit(12).toList();
    }
    List<String> keys = forBatch.stream().map(Candidate::sectionKey).toList();
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("side", request.side().name());
    variables.put("output_language", request.outputLanguage());
    variables.put("profile", request.profile());
    variables.put("outline", outline);
    variables.put(
        "rule_findings",
        ruleFindings.stream()
            .filter(r -> ids.contains(String.valueOf(r.get("clauseId"))))
            .toList());
    variables.put("expected_topics_missing", expectedTopicsMissing);
    variables.put("candidate_sections", forBatch);
    variables.put("clauses", clauseJson(batch));
    return new Batch(ids, variables, keys);
  }

  private List<Finding> runBatch(
      ModelSession session,
      AnalysisRequest request,
      List<ClauseView> batch,
      List<Map<String, Object>> outline,
      CandidateSet candidates,
      List<Map<String, Object>> ruleFindings,
      List<Map<String, Object>> expectedTopicsMissing,
      int[] splits,
      boolean mayFallBackToSplit) {
    Batch planned = batch(request, batch, outline, candidates, ruleFindings, expectedTopicsMissing);
    ModelGateway.Result result;
    try {
      result = session.call(Stage.RISK, PROMPT, planned.variables(), planned.enums());
    } catch (ModelOutputInvalidException e) {
      if (!mayFallBackToSplit || batch.size() < 2) {
        throw e;
      }
      synchronized (splits) {
        splits[0]++;
      }
      int half = batch.size() / 2;
      List<Finding> out = new ArrayList<>();
      out.addAll(
          runBatch(
              session,
              request,
              batch.subList(0, half),
              outline,
              candidates,
              ruleFindings,
              expectedTopicsMissing,
              splits,
              false));
      out.addAll(
          runBatch(
              session,
              request,
              batch.subList(half, batch.size()),
              outline,
              candidates,
              ruleFindings,
              expectedTopicsMissing,
              splits,
              false));
      return out;
    }
    return parse(result.output(), planned.clauseIds(), request.outputLanguage());
  }

  static List<Finding> parse(JsonNode output, List<String> batchClauseIds, String language) {
    List<Finding> out = new ArrayList<>();
    if (!"OK".equals(output.path("status").asText())) {
      return out;
    }
    for (JsonNode f : output.path("findings")) {
      FindingKind kind = FindingKind.valueOf(f.path("kind").asText());
      List<String> clauseIds = new ArrayList<>();
      for (JsonNode id : f.path("clause_ids")) {
        if (batchClauseIds.contains(id.asText()) && !clauseIds.contains(id.asText())) {
          clauseIds.add(id.asText());
        }
      }
      if (kind != FindingKind.OMISSION && clauseIds.isEmpty()) {
        continue; // a finding about no clause cannot be shown (api.md §5.8)
      }
      List<ClaimedCitation> claims = new ArrayList<>();
      for (JsonNode c : f.path("citations")) {
        claims.add(
            ClaimedCitation.llm(
                c.path("section_key").asText(),
                c.path("quote").asText(),
                SensitiveText.of(c.path("claim").asText()),
                language));
      }
      out.add(
          new Finding(
              "p" + (out.size() + 1),
              SensitiveText.of(f.path("title").asText()),
              kind,
              Severity.valueOf(f.path("severity").asText()),
              f.path("non_obvious").asBoolean(false)
                  || kind == FindingKind.OMISSION
                  || kind == FindingKind.INTERACTION,
              FindingSource.LLM_ANALYSIS,
              f.path("clause_type").asText(),
              clauseIds,
              SensitiveText.of(f.path("explanation").asText()),
              SensitiveText.of(f.path("why_it_matters").asText()),
              Math.max(0, Math.min(1, f.path("confidence").asDouble(0.5))),
              null,
              claims,
              FindingVerification.UNCERTAIN,
              List.of(),
              List.of(),
              null,
              null));
    }
    return out;
  }

  static List<Map<String, Object>> outline(List<ClauseView> clauses) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ClauseView view : clauses) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("clause_id", view.clause().id());
      item.put("type", String.valueOf(view.clause().type()));
      item.put("facts", factJson(view.facts()));
      out.add(item);
    }
    return out;
  }

  static List<Map<String, Object>> clauseJson(List<ClauseView> batch) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ClauseView view : batch) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", view.clause().id());
      item.put("type", String.valueOf(view.clause().type()));
      item.put("text", view.clause().text().reveal());
      item.put("facts", factJson(view.facts()));
      item.put(
          "instruction_like", view.clause().flags().contains(ClauseFlag.INSTRUCTION_LIKE_TEXT));
      out.add(item);
    }
    return out;
  }

  private static List<Map<String, Object>> factJson(List<ClauseFact> facts) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ClauseFact fact : facts) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("parameter", fact.parameter().name());
      item.put("value", fact.value());
      item.put("unit", fact.unit().name());
      out.add(item);
    }
    return out;
  }

  /**
   * Up to {@code max} batches of contiguous clauses, near-equal in size; a boundary is moved to a
   * change of clause type when that is at most one clause away (architecture §8.5).
   */
  static List<List<ClauseView>> batches(List<ClauseView> clauses, int max) {
    List<List<ClauseView>> out = new ArrayList<>();
    if (clauses.isEmpty()) {
      return out;
    }
    int count = Math.min(max, clauses.size());
    int target = (clauses.size() + count - 1) / count;
    int from = 0;
    while (from < clauses.size()) {
      int to = Math.min(clauses.size(), from + target);
      if (to < clauses.size()
          && clauses.get(to).clause().type() == clauses.get(to - 1).clause().type()
          && to + 1 < clauses.size()
          && out.size() < count - 1
          && clauses.get(to + 1).clause().type() != clauses.get(to).clause().type()) {
        to++;
      }
      out.add(clauses.subList(from, to));
      from = to;
    }
    return out;
  }

  /** The distinct section keys cited by a list of findings. */
  static Set<String> citedKeys(List<Finding> findings) {
    Set<String> keys = new LinkedHashSet<>();
    findings.forEach(f -> f.claims().forEach(c -> keys.add(c.sectionKey())));
    return keys;
  }
}
