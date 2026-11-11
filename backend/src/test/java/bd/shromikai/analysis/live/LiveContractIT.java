package bd.shromikai.analysis.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisPipeline;
import bd.shromikai.analysis.AnalysisRequest;
import bd.shromikai.analysis.AnalysisService;
import bd.shromikai.analysis.AnalysisSubject;
import bd.shromikai.analysis.Side;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.VerifiedOnlyScanner;
import bd.shromikai.testsupport.live.LiveEnv;
import bd.shromikai.testsupport.live.LiveSpendLedger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * FR-47 (live): the shortest synthetic contract, one side, with the real models, the real corpus
 * and the real verifier; the calls are recorded as fixtures and the cost is read from the ledger.
 * Runs only with {@code -PliveOk=record} and only for the contract named by {@code -Pcontract=...}
 * (default the shortest, {@code syn-en-02}). The contract is synthetic: no real contract is ever
 * sent (R-02). The assertions are the ones of the acceptance check: at least three risks and every
 * shown citation {@code VERIFIED}; a failure is reported, not weakened.
 */
@Tag("live")
@Requirement("FR-47")
@SpringBootTest
class LiveContractIT extends LiveBase {

  private static final String CONTRACT = System.getProperty("shromikai.live.contract", "syn-en-02");

  @Autowired AnalysisService analyses;
  @Autowired AnalysisPipeline pipeline;
  @Autowired LiveSpendLedger ledger;
  @Autowired JdbcClient jdbc;

  @BeforeAll
  static void optIn() {
    assumeTrue(LiveEnv.optedIn("record"), "live recording: run with -PliveOk=record");
    Run.purpose = "record:" + CONTRACT;
    Run.caseId = CONTRACT + "-worker";
    Run.maxCalls = 80;
    Run.record = true;
  }

  @Test
  void shouldAnalyseTheShortestSyntheticContractWithRealModelsAndRecordIt() throws IOException {
    long spentBefore = ledger.spentMicros();
    AnalysisJob job = analyses.analyseText(SyntheticContracts.text(CONTRACT));
    try {
      pipeline.run(job, AnalysisRequest.of(Side.WORKER), AnalysisSubject.dev());
      report(job, spentBefore);
      assertThat(job.failure()).as("the pipeline failed").isNull();
      assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
      var findings = job.outcome().findings();
      assertThat(findings.size()).as("at least three risks").isGreaterThanOrEqualTo(3);
      for (var finding : findings) {
        for (var citation : finding.citations()) {
          assertThat(citation.verification().status().name()).isEqualTo("VERIFIED");
        }
        if (finding.verification().name().equals("UNCERTAIN")) {
          assertThat(finding.citations()).isEmpty();
        }
      }
      var json = new ObjectMapper().findAndRegisterModules().valueToTree(findings);
      assertThat(VerifiedOnlyScanner.violations(json)).isEmpty();
    } finally {
      jdbc.sql("delete from analysis_run where id = :id").param("id", job.id()).update();
    }
  }

  /** Writes what the run cost and found to the (git-ignored) ledger folder; never contract text. */
  private void report(AnalysisJob job, long spentBefore) throws IOException {
    StringBuilder out = new StringBuilder();
    out.append("contract: ").append(CONTRACT).append(" (WORKER)\n");
    out.append("status: ").append(job.status()).append('\n');
    if (job.failure() != null) {
      out.append("failure: ").append(job.failure().type().code()).append('\n');
    }
    out.append("ledger cost of this run (all providers): US$")
        .append(
            String.format(Locale.ROOT, "%.4f", (ledger.spentMicros() - spentBefore) / 1_000_000.0))
        .append('\n');
    out.append("ledger total: US$")
        .append(String.format(Locale.ROOT, "%.4f", ledger.spentMicros() / 1_000_000.0))
        .append(" of US$3.00\n");
    out.append("calls: ").append(ledger.callsFor("record:" + CONTRACT)).append('\n');
    job.trace()
        .forEach(
            e ->
                out.append(
                    String.format(
                        Locale.ROOT,
                        "%-9s %6d ms  %-7s in=%s out=%s cost=%s model=%s%n",
                        e.stage(),
                        e.durationMs(),
                        e.outcome(),
                        e.tokensIn(),
                        e.tokensOut(),
                        e.costUsdMicros(),
                        e.modelId())));
    if (job.outcome() != null) {
      out.append("findings: ").append(job.outcome().findings().size()).append('\n');
      job.outcome()
          .findings()
          .forEach(
              f ->
                  out.append(
                      String.format(
                          "  %s %s %s %s citations=%d review=%s%n",
                          f.id(),
                          f.kind(),
                          f.severity(),
                          f.verification(),
                          f.citations().size(),
                          f.reviewItemId() != null)));
      out.append("warnings: ")
          .append(job.outcome().warnings().stream().map(w -> w.code()).toList())
          .append('\n');
    }
    Path file = Path.of("..", ".live-spend", "last-run.txt");
    Files.createDirectories(file.getParent());
    Files.writeString(file, out.toString());
  }
}
