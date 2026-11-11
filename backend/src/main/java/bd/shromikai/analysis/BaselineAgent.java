package bd.shromikai.analysis;

import bd.shromikai.law.LawReadService;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.text.OverlapChecker;
import bd.shromikai.verify.CitationOrigin;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.ReasonCode;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The plain-LLM baseline of compare mode (FR-59): the {@code baseline} prompt reads the redacted
 * document with no retrieval and no verifier; each citation it writes is then run through the
 * verifier (origin {@code BASELINE}), so an invented section ends {@code REJECTED} with {@code
 * SECTION_NOT_FOUND}. The document goes through the job's egress guard like every cloud call
 * (R-03). A failure leaves the baseline {@code UNAVAILABLE}; the ShromikAI result is unaffected.
 */
@Component
public final class BaselineAgent {

  private static final Logger LOG = LoggerFactory.getLogger(BaselineAgent.class);
  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  private final ModelGateway gateway;
  private final CostCalculator cost;
  private final CitationVerifier verifier;
  private final LawReadService law;
  private final AnalysisProperties properties;
  private final DailyBudget dailyBudget;
  private final Clock clock;

  public BaselineAgent(
      ModelGateway gateway,
      CostCalculator cost,
      CitationVerifier verifier,
      LawReadService law,
      AnalysisProperties properties,
      DailyBudget dailyBudget,
      Clock clock) {
    this.gateway = gateway;
    this.cost = cost;
    this.verifier = verifier;
    this.law = law;
    this.properties = properties;
    this.dailyBudget = dailyBudget;
    this.clock = clock;
  }

  /** Runs the baseline for a finished analysis and stores it on the job. */
  public void run(AnalysisJob job) {
    AnalysisRequest request = job.request();
    if (job.status() != AnalysisJob.Status.ANALYSED || request == null) {
      job.baseline(Baseline.unavailable("analysis-failed"));
      return;
    }
    try {
      job.baseline(compute(job, request));
    } catch (EgressBlockedException blocked) {
      job.baseline(Baseline.unavailable("privacy-guard-blocked"));
    } catch (ModelException e) {
      job.baseline(Baseline.unavailable(e.reasonCode().replaceAll("[^a-z0-9-]", "-")));
    } catch (RuntimeException e) {
      LOG.error("baseline failed: {}", e.getClass().getSimpleName());
      job.baseline(Baseline.unavailable("internal-error"));
    }
  }

  private Baseline compute(AnalysisJob job, AnalysisRequest request) {
    List<String> texts = job.clauses().stream().map(c -> c.text().reveal()).toList();
    ModelSession session =
        new ModelSession(
            gateway,
            cost,
            job.placeholders(),
            properties.budget().perAnalysisUsdMicros(),
            properties.budget().perAnalysisTokens());
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("side", request.side().name());
    variables.put("output_language", request.outputLanguage());
    variables.put("document", String.join("\n\n", texts));
    ModelGateway.Result result;
    try {
      result = session.call(Stage.RISK, "baseline", variables);
    } finally {
      dailyBudget.record(session.costUsdMicros());
    }
    JsonNode out = result.output();
    if (!"OK".equals(out.path("status").asText())) {
      return new Baseline("OK", null, result.modelId(), List.of());
    }
    List<ClaimedCitation> claims = new ArrayList<>();
    List<List<ClaimedCitation>> perFinding = new ArrayList<>();
    for (JsonNode f : out.path("findings")) {
      List<ClaimedCitation> own = new ArrayList<>();
      for (JsonNode c : f.path("citations")) {
        own.add(
            ClaimedCitation.of(
                c.path("section_key").asText(),
                c.path("quote").asText(),
                SensitiveText.of(c.path("claim").asText()),
                request.outputLanguage(),
                CitationOrigin.BASELINE,
                null,
                null));
      }
      claims.addAll(own);
      perFinding.add(own);
    }
    VerificationContext context =
        new VerificationContext(
            clock.instant().atZone(DHAKA).toLocalDate(),
            properties.analysis().jurisdictions(),
            law.corpusRelease() == null ? "none" : law.corpusRelease(),
            Set.of(),
            1,
            session.egress(),
            new OverlapChecker(texts));
    Map<java.util.UUID, Verdict> verdicts = new java.util.HashMap<>();
    verifier.verifyAll(claims, context).forEach(v -> verdicts.put(v.result().citationId(), v));
    List<Baseline.Finding> findings = new ArrayList<>();
    int i = 0;
    for (JsonNode f : out.path("findings")) {
      List<Baseline.Checked> checked = new ArrayList<>();
      for (ClaimedCitation claim : perFinding.get(i++)) {
        Verdict v = verdicts.get(claim.citationId());
        checked.add(
            new Baseline.Checked(
                claim,
                v.result().status(),
                v.result().reasonCodes().stream().map(ReasonCode::name).toList()));
      }
      findings.add(
          new Baseline.Finding(
              SensitiveText.of(f.path("title").asText()),
              f.path("severity").asText(),
              SensitiveText.of(f.path("explanation").asText()),
              checked));
    }
    return new Baseline("OK", null, result.modelId(), findings);
  }
}
