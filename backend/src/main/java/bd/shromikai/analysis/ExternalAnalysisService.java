package bd.shromikai.analysis;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.json.SnakeCase;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.shared.text.OverlapChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A synchronous analysis for an outside client of an organisation, used by the MCP tool {@code
 * analyze_employment_contract} (FR-66). The full pipeline runs with the privacy shield; the result
 * keeps the placeholders and is never re-identified; every text except {@code suggested_clause} is
 * checked against the redacted contract and replaced by a fixed notice if it would repeat 8 tokens
 * of it (R-02, glossary "contract text"). The job is removed from memory as soon as the result is
 * built, so nothing outlives the call.
 */
@Service
public class ExternalAnalysisService {

  /** Shown in place of a text that would have repeated the contract. */
  static final String WITHHELD = "[withheld: this text repeated the contract]";

  /** The tool result and what the analysis cost, for metering. */
  public record Result(ObjectNode content, long costUsdMicros) {}

  private final AnalysisRunner runner;
  private final AnalysisResults results;
  private final JobStore jobs;
  private final ObjectMapper mapper;

  public ExternalAnalysisService(
      AnalysisRunner runner, AnalysisResults results, JobStore jobs, ObjectMapper mapper) {
    this.runner = runner;
    this.results = results;
    this.jobs = jobs;
    this.mapper = mapper;
  }

  /**
   * @param keyId the API key, hashed into the analysis subject
   * @param orgId the key's organisation, which owns the run's metadata
   * @param timeout how long to wait for the pipeline
   * @throws ProblemException the failure of the analysis, {@code analysis-timeout} when it did not
   *     finish in time, {@code concurrency-limit} when the organisation has too many running
   */
  public Result analyse(
      UUID keyId, UUID orgId, String text, Side side, String language, Duration timeout) {
    AnalysisSubject subject =
        new AnalysisSubject("API_KEY", Hashing.sha256Hex(keyId.toString()), orgId, "MCP");
    AnalysisRequest request = new AnalysisRequest(side, language, Map.of());
    AnalysisJob job = runner.startText(subject, request, text, () -> {});
    try {
      if (!runner.awaitFinished(job, timeout)) {
        throw new ProblemException(ProblemType.ANALYSIS_TIMEOUT);
      }
      if (job.status() == AnalysisJob.Status.FAILED && job.failure() != null) {
        throw job.failure();
      }
      AnalysisJob.Outcome outcome = job.outcome();
      if (job.status() != AnalysisJob.Status.ANALYSED || outcome == null) {
        throw new ProblemException(ProblemType.INTERNAL_ERROR);
      }
      return new Result(build(job, outcome), outcome.costUsdMicros());
    } finally {
      jobs.delete(job.id());
    }
  }

  private ObjectNode build(AnalysisJob job, AnalysisJob.Outcome outcome) {
    OverlapChecker contract =
        new OverlapChecker(job.clauses().stream().map(c -> c.text().reveal()).toList());
    boolean[] withheld = {false};
    java.util.function.UnaryOperator<String> safe =
        value -> {
          if (contract.overlaps(value)) {
            withheld[0] = true;
            return WITHHELD;
          }
          return value;
        };
    ObjectNode out = mapper.createObjectNode();
    out.put("analysis_id", job.id().toString());
    ArrayNode findings = out.putArray("findings");
    for (Finding f : outcome.findings()) {
      boolean verified = f.verification() == FindingVerification.VERIFIED;
      ObjectNode node = findings.addObject();
      node.put("id", f.id());
      node.put("title", safe.apply(f.title().reveal()));
      node.put("kind", f.kind().name());
      node.put("severity", f.severity().name());
      node.put("non_obvious", f.nonObvious());
      node.put("explanation", safe.apply(f.explanation().reveal()));
      node.put("verification_status", f.verification().name());
      node.set(
          "citations",
          verified ? SnakeCase.keys(mapper.valueToTree(f.citations())) : mapper.createArrayNode());
      Redline redline =
          verified
              ? outcome.redlines().stream()
                  .filter(r -> r.findingId().equals(f.id()))
                  .findFirst()
                  .orElse(null)
              : null;
      if (redline == null) {
        node.putNull("suggested_clause");
      } else {
        node.put("suggested_clause", redline.proposedText().reveal()); // exempt, keeps placeholders
      }
    }
    out.set("summary", SnakeCase.keys(results.summary(outcome)));
    if (outcome.ticket() == null) {
      out.putNull("review_ticket");
    } else {
      out.putObject("review_ticket")
          .put("ticket_id", outcome.ticket().ticketId().toString())
          .put("expires_at", outcome.ticket().expiresAt().toString());
    }
    ObjectNode provenance = out.putObject("provenance");
    provenance.put("pipeline_version", AnalysisPipeline.PIPELINE_VERSION);
    provenance.put("corpus_release", outcome.corpusRelease());
    provenance.set("prompt_versions", mapper.valueToTree(outcome.promptVersions()));
    provenance.set("models", mapper.valueToTree(outcome.models()));
    provenance.put("verifier_independence", outcome.verifierIndependence());
    ArrayNode warnings = out.putArray("warnings");
    outcome
        .warnings()
        .forEach(w -> warnings.addObject().put("code", w.code()).put("message", w.message()));
    if (withheld[0]) {
      warnings
          .addObject()
          .put("code", "text-withheld")
          .put("message", "A text that repeated the contract was replaced by a notice.");
    }
    return out;
  }
}
