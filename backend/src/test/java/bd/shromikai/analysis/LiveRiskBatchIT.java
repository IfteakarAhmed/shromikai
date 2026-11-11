package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import bd.shromikai.analysis.live.LiveBase;
import bd.shromikai.rules.ClauseFact;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.live.LiveEnv;
import bd.shromikai.testsupport.live.LiveSpendLedger;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Live attempt 1 of the risk step (ADR-0016): ONE batch, ONE live {@code risk-analysis} call, the
 * stop reason checked. The cheap steps before it are replayed from the recordings of earlier runs
 * (no cost) or made live (Haiku, a fraction of a cent). A second risk call, which a gateway repair
 * after invalid output would be, is refused by the capped port: if the answer is truncated or
 * invalid the test stops there, with the cost so far in the report. Nothing here runs the other
 * batches, verification or counter-offers. The contract is synthetic (R-02). The ledger stays under
 * US$1.20 for the contract; a larger spend is refused.
 */
@Tag("live")
@Requirement("FR-37")
@SpringBootTest
class LiveRiskBatchIT extends LiveBase {

  private static final String CONTRACT = "syn-en-02";

  @Autowired AnalysisService analyses;
  @Autowired CandidateRetriever retriever;
  @Autowired FactExtractor factExtractor;
  @Autowired RiskAgent risk;
  @Autowired ModelGateway gateway;
  @Autowired CostCalculator cost;
  @Autowired LiveSpendLedger ledger;

  @BeforeAll
  static void optIn() {
    assumeTrue(LiveEnv.optedIn("record"), "live recording: run with -PliveOk=record");
    LiveBase.Run.purpose = "record:" + CONTRACT;
    LiveBase.Run.caseId = CONTRACT + "-worker";
    LiveBase.Run.maxCalls = 40;
    LiveBase.Run.record = true;
    LiveBase.Run.localPiiStandInFor = CONTRACT;
    LiveBase.Run.perPromptLimits = Map.of("risk-analysis", 1);
    LiveBase.Run.contractPrefix = "record:" + CONTRACT;
    LiveBase.Run.contractCapMicros = 1_200_000;
  }

  @Test
  void shouldGetOneValidRiskBatchWithTheStopReasonChecked() throws IOException {
    long contractBefore = ledger.spentOn("record:" + CONTRACT);
    AnalysisJob job = analyses.analyseText(SyntheticContracts.text(CONTRACT));
    StringBuilder report = new StringBuilder();
    report
        .append("live attempt 1: ONE risk-analysis v2 batch of ")
        .append(CONTRACT)
        .append(" (WORKER)\n");
    report.append("contract spend before: US$").append(usd(contractBefore)).append('\n');
    try {
      assertThat(job.status()).as("P5 steps").isEqualTo(AnalysisJob.Status.CLASSIFIED);
      ModelSession session =
          new ModelSession(gateway, cost, job.placeholders(), 1_000_000, 400_000);
      var retrieved = retriever.retrieve(job.clauses(), session.egress());
      Map<String, List<ClauseFact>> facts =
          factExtractor.extract(session, Stage.RULES, job.clauses());
      List<RiskAgent.ClauseView> views = new ArrayList<>();
      job.clauses()
          .forEach(
              c -> views.add(new RiskAgent.ClauseView(c, facts.getOrDefault(c.id(), List.of()))));
      var plan =
          risk.plan(
              AnalysisRequest.of(Side.WORKER), views, retrieved.candidates(), List.of(), List.of());
      report.append("batches planned: ").append(plan.size()).append(" (only the first is run)\n");
      for (int i = 0; i < plan.size(); i++) {
        report
            .append("  batch ")
            .append(i + 1)
            .append(": clauses ")
            .append(plan.get(i).clauseIds())
            .append(", candidate sections ")
            .append(plan.get(i).candidateKeys().size())
            .append('\n');
      }
      RiskAgent.Batch first = plan.getFirst();

      ModelGateway.Result result;
      try {
        result = session.call(Stage.RISK, "risk-analysis", first.variables(), first.enums());
      } catch (RuntimeException e) {
        report
            .append("\nSTOPPED: the risk call failed: ")
            .append(e.getClass().getSimpleName())
            .append(" ")
            .append(e.getMessage())
            .append('\n');
        throw e;
      }

      JsonNode out = result.output();
      JsonNode findings = out.path("findings");
      long tokensOut = result.usage().outputTokens();
      report.append("\nstop_reason: ").append(result.stopReason()).append('\n');
      report.append("model: ").append(result.modelId()).append('\n');
      report
          .append("status/reason: ")
          .append(out.path("status").asText())
          .append('/')
          .append(out.path("reason").asText())
          .append('\n');
      report.append("raw finding count: ").append(findings.size()).append('\n');
      report
          .append("tokens in/out: ")
          .append(result.usage().totalInputTokens())
          .append('/')
          .append(tokensOut)
          .append("  (cap 3000, used ")
          .append(String.format(Locale.ROOT, "%.0f%%", 100.0 * tokensOut / 3000))
          .append(")\n");
      report
          .append("latency: ")
          .append(result.latencyMs())
          .append(" ms (≈ ")
          .append(
              String.format(
                  Locale.ROOT, "%.0f", tokensOut * 1000.0 / Math.max(1, result.latencyMs())))
          .append(" output tokens per second including prefill)\n");
      report
          .append("cost of this call: US$")
          .append(usd(cost.usdMicros(result.modelId(), result.usage())))
          .append('\n');
      int i = 0;
      for (JsonNode f : findings) {
        report.append(
            String.format(
                "%n#%d %s %s non_obvious=%s clauses=%s type=%s confidence=%s%n",
                ++i,
                f.path("kind").asText(),
                f.path("severity").asText(),
                f.path("non_obvious").asText(),
                f.path("clause_ids"),
                f.path("clause_type").asText(),
                f.path("confidence").asText()));
        report.append("  title: ").append(f.path("title").asText()).append('\n');
        report.append("  explanation: ").append(f.path("explanation").asText()).append('\n');
        for (JsonNode c : f.path("citations")) {
          report.append("  cites ").append(c.path("section_key").asText()).append('\n');
          report.append("    quote: ").append(c.path("quote").asText()).append('\n');
          report.append("    claim: ").append(c.path("claim").asText()).append('\n');
        }
        if (f.path("citations").isEmpty()) {
          report.append("  (no citation)\n");
        }
      }
      assertThat(result.stopReason()).as("stop_reason").isEqualTo("end_turn");
      assertThat(out.path("status").asText()).isEqualTo("OK");
      assertThat(findings.size()).isLessThanOrEqualTo(3);
    } finally {
      report
          .append("\ncontract spend after: US$")
          .append(usd(ledger.spentOn("record:" + CONTRACT)))
          .append(" (cap US$1.20); ledger total US$")
          .append(usd(ledger.spentMicros()))
          .append(" of US$3.00\n");
      Path file = Path.of("..", ".live-spend", "risk-batch-1.txt");
      Files.createDirectories(file.getParent());
      Files.writeString(file, report.toString());
    }
  }

  private static String usd(long micros) {
    return String.format(Locale.ROOT, "%.4f", micros / 1_000_000.0);
  }
}
