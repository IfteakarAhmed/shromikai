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
    Run.maxCalls = 40;
    // Replay everything already recorded; go live only for the verifier (its prompt has a new
    // version, ADR-0019), CORRECT (repair, its verification) and the counter-offers. A call that a
    // recording should have answered but did not (the prompt or
    // the input changed) is a live call of a prompt with limit 0 and stops the run at once.
    Run.perPromptLimits =
        java.util.Map.of(
            "clause-classify", 0,
            "clause-facts", 0,
            "query-rewrite", 0,
            "risk-analysis", 0,
            "risk-citation-repair", 4,
            "verify-support-claude", 30,
            "counter-offer", 11);
    Run.contractPrefix = "record:" + CONTRACT;
    // An extra US$0.20 for this run, and never more than US$1.20 for the contract in total.
    Run.contractCapMicros =
        Math.min(
            1_200_000,
            new bd.shromikai.testsupport.live.LiveSpendLedger().spentOn("record:" + CONTRACT)
                + 200_000);
    Run.record = true;
    Run.localPiiStandInFor = CONTRACT;
  }

  @Test
  void shouldAnalyseTheShortestSyntheticContractWithRealModelsAndRecordIt() throws IOException {
    long spentBefore = ledger.spentMicros();
    int entriesBefore = ledger.entryCount();
    AnalysisJob job = analyses.analyseText(SyntheticContracts.text(CONTRACT));
    try {
      if (job.status() != AnalysisJob.Status.CLASSIFIED) {
        report(job, spentBefore, entriesBefore);
        throw new AssertionError(
            "The P5 steps failed before the pipeline could start: "
                + (job.failure() == null ? "?" : job.failure().type().code())
                + " (see .live-spend/last-run.txt)");
      }
      pipeline.run(job, AnalysisRequest.of(Side.WORKER), AnalysisSubject.dev());
      report(job, spentBefore, entriesBefore);
      assertThat(job.failure()).as("the pipeline failed").isNull();
      assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
      var findings = job.outcome().findings();
      assertThat(findings.size()).as("at least three risks").isGreaterThanOrEqualTo(3);
      // The success criteria of a recording: nothing invalid or truncated, no redline dropped.
      assertThat(Run.TRIPWIRE.get()).as("an invalid or truncated answer").isFalse();
      assertThat(job.outcome().warnings())
          .as("dropped counter-offers")
          .noneMatch(w -> w.code().equals("counter-offer-dropped"));
      for (var finding : findings) {
        for (var citation : finding.citations()) {
          assertThat(citation.verification().status().name()).isEqualTo("VERIFIED");
        }
        if (finding.verification().name().equals("UNCERTAIN")) {
          assertThat(finding.citations()).isEmpty();
        }
      }
      // What a user can see of a finding is its citations (the claims are internal): scan those.
      var mapper = new ObjectMapper().findAndRegisterModules();
      var shown = mapper.createArrayNode();
      findings.forEach(f -> shown.add(mapper.valueToTree(f.citations())));
      assertThat(VerifiedOnlyScanner.violations(shown)).isEmpty();
      assertThat(VerifiedOnlyScanner.citations(shown)).isPositive();
    } finally {
      jdbc.sql("delete from analysis_run where id = :id").param("id", job.id()).update();
    }
  }

  /** Writes what the run cost and found to the (git-ignored) ledger folder; never contract text. */
  private void report(AnalysisJob job, long spentBefore, int entriesBefore) throws IOException {
    StringBuilder out = new StringBuilder();
    out.append("contract: ").append(CONTRACT).append(" (WORKER)\n");
    out.append("local PII step: ground-truth entity list stand-in (local model too slow here)\n");
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
    if (job.outcome() != null) {
      out.append("\n--- findings in full (synthetic contract, placeholders not names) ---\n");
      for (var f : job.outcome().findings()) {
        out.append(
            String.format(
                "%n%s [%s %s %s] source=%s nonObvious=%s confidence=%.2f clauses=%s clauseType=%s%n",
                f.id(),
                f.kind(),
                f.severity(),
                f.verification(),
                f.source(),
                f.nonObvious(),
                f.confidence(),
                f.clauseIds(),
                f.clauseType()));
        out.append("  title: ").append(f.title().reveal()).append('\n');
        out.append("  explanation: ").append(f.explanation().reveal()).append('\n');
        out.append("  why it matters: ").append(f.whyItMatters().reveal()).append('\n');
        for (var c : f.citations()) {
          var v = c.verification();
          out.append("  VERIFIED citation ")
              .append(c.sectionKey())
              .append(" (attempt ")
              .append(v.attempt())
              .append(")\n");
          out.append("    heading: ")
              .append(c.headingBn() != null ? c.headingBn() : c.headingEn())
              .append('\n');
          out.append("    quote shown (stored statute text): ").append(c.quote()).append('\n');
          out.append("    quote similarity: ")
              .append(v.checks().quoteMatch().similarity())
              .append('\n');
          var sup = v.checks().support();
          out.append("    support: ")
              .append(sup.label())
              .append(" confidence=")
              .append(sup.confidence())
              .append(" independence=")
              .append(sup.independence())
              .append(" model=")
              .append(sup.modelId())
              .append('\n');
          out.append("    support reason: ").append(sup.reason()).append('\n');
          out.append("    source: ").append(c.sourceUrl()).append('\n');
        }
        if (f.citations().isEmpty()) {
          out.append("  no citation shown; reason codes: ").append(f.reasonCodes()).append('\n');
        }
        out.append("  redline: ")
            .append(f.redlineId())
            .append("  review item: ")
            .append(f.reviewItemId() != null)
            .append('\n');
      }
      out.append("\n--- redlines ---\n");
      for (var r : job.outcome().redlines()) {
        out.append(r.id())
            .append(" for ")
            .append(r.findingId())
            .append(" ")
            .append(r.action())
            .append(" attempts=")
            .append(r.attempts())
            .append(" rules=")
            .append(r.ruleIds())
            .append('\n');
        out.append("  original: ")
            .append(r.originalText() == null ? null : r.originalText().reveal())
            .append('\n');
        out.append("  proposed: ").append(r.proposedText().reveal()).append('\n');
        out.append("  rationale: ").append(r.rationale().reveal()).append('\n');
      }
      out.append("\n--- clauses (redacted) ---\n");
      for (var c : job.clauses()) {
        out.append(c.id())
            .append(" ")
            .append(c.type())
            .append(": ")
            .append(c.text().reveal())
            .append('\n');
      }
    }
    if (job.outcome() != null) {
      out.append("\n--- every citation the verifier checked (finding, section, attempt) ---\n");
      for (var v : job.outcome().verdicts()) {
        out.append(
            String.format(
                "%s %s attempt %d: %s  reasons=%s%n    support: %s confidence=%s%n    verifier's reason: %s%n",
                v.findingId(),
                v.sectionKey(),
                v.attempt(),
                v.status(),
                v.reasonCodes(),
                v.supportLabel(),
                v.supportConfidence(),
                v.supportReason()));
      }
      long verified =
          job.outcome().verdicts().stream()
              .filter(v -> v.status().name().equals("VERIFIED"))
              .count();
      long uncertain =
          job.outcome().verdicts().stream()
              .filter(v -> v.status().name().equals("UNCERTAIN"))
              .count();
      long rejected =
          job.outcome().verdicts().stream()
              .filter(v -> v.status().name().equals("REJECTED"))
              .count();
      out.append("citation verdicts: VERIFIED=")
          .append(verified)
          .append(" UNCERTAIN=")
          .append(uncertain)
          .append(" REJECTED=")
          .append(rejected)
          .append('\n');
    }
    out.append("\n--- cost of this run's live calls by prompt (ledger) ---\n");
    java.util.Map<String, long[]> byPrompt = new java.util.TreeMap<>();
    for (var e : ledger.entriesFrom(entriesBefore)) {
      String prompt = e.purpose().substring(e.purpose().lastIndexOf(':') + 1);
      long[] sum = byPrompt.computeIfAbsent(prompt + " (" + e.modelId() + ")", k -> new long[4]);
      sum[0]++;
      sum[1] += e.tokensIn();
      sum[2] += e.tokensOut();
      sum[3] += e.costMicros();
    }
    byPrompt.forEach(
        (k, v) ->
            out.append(
                String.format(
                    Locale.ROOT,
                    "%-48s calls=%d in=%d out=%d cost=US$%.4f%n",
                    k,
                    v[0],
                    v[1],
                    v[2],
                    v[3] / 1_000_000.0)));
    out.append("\n--- trace details (content-free) ---\n");
    job.trace().forEach(e -> out.append(e.stage()).append(' ').append(e.detail()).append('\n'));
    Path file = Path.of("..", ".live-spend", "last-run.txt");
    Files.createDirectories(file.getParent());
    Files.writeString(file, out.toString());
  }
}
