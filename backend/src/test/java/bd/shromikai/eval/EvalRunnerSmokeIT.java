package bd.shromikai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.analysis.Side;
import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.CitationCase;
import bd.shromikai.eval.harness.EvalHarness;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.P6Stubs;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-80, FR-83: the evaluation runner end to end on two synthetic contracts, for ShromikAI and the
 * baseline, plus a few adversarial citations, writes both report files, and the hard safety gate
 * holds on its results. The model answers are <strong>scripted stand-ins</strong> ({@code P6Stubs})
 * and the act is fictional (R-10): this proves the runner's mechanics, not model quality. The real
 * numbers come from {@code ./gradlew evaluate} against the real corpus and models.
 */
@IntegrationTest
@ActiveProfiles({"test", "dev"})
@org.springframework.context.annotation.Import(EvalHarness.class)
@Requirement("FR-80")
@Requirement("FR-83")
class EvalRunnerSmokeIT {

  @Autowired EvalHarness harness;
  @Autowired FakeChatPort fake;
  @Autowired JdbcClient jdbc;

  @TempDir Path reports;

  /** The stand-in baseline: one real section with its exact text, one invented section. */
  private static final String BASELINE =
      """
      {"status":"OK","reason":"NONE","findings":[{"title":"Probation","severity":"HIGH",
       "explanation":"Stand-in.","citations":[
        {"section_key":"XV-LAB:1@original","quote":"A probation period shall not exceed six months.",
         "claim":"Probation may last at most six months."},
        {"section_key":"XV-LAB:99@original","quote":"No such section exists in the fictional act.",
         "claim":"An invented rule."}]}]}
      """;

  @BeforeEach
  void standIns() {
    FictionalCorpus.load(jdbc);
    fake.reset();
    P6Stubs.install(fake);
    fake.respondWith("baseline", request -> TestGateways.json(BASELINE));
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from analysis_run").update();
    FictionalCorpus.clean(jdbc);
  }

  @Test
  void shouldRunTwoContractsAndWriteBothReportFiles() throws Exception {
    List<AnalysisCase> cases = new ArrayList<>();
    for (String contract : List.of("syn-en-02", "syn-bn-01")) {
      cases.addAll(
          harness.run(
              contract,
              SyntheticContracts.text(contract),
              Side.WORKER,
              List.of("shromikai", "baseline"),
              SyntheticContracts.allValues(contract)));
    }
    // The baseline alone on a contract that ShromikAI does not run (classified job only).
    List<AnalysisCase> baselineOnly =
        harness.run(
            "syn-en-01",
            SyntheticContracts.text("syn-en-01"),
            Side.WORKER,
            List.of("baseline"),
            SyntheticContracts.allValues("syn-en-01"));
    assertThat(baselineOnly)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.outcome()).isEqualTo("ANALYSED");
              assertThat(c.claims()).isNotEmpty();
            });
    cases.addAll(baselineOnly);
    String valid = FictionalCorpus.SECTIONS.get("5");
    List<AdversarialCase> adversarial =
        List.of(
            new AdversarialCase(
                "ac-001",
                "NONEXISTENT_SECTION",
                "XV-LAB:404@original",
                valid.substring(0, 60),
                "An invented section.",
                "NOT_VERIFIED",
                "smoke",
                null),
            new AdversarialCase(
                "ac-002",
                "VALID_CONTROL",
                FictionalCorpus.key("5"),
                valid.substring(0, 80),
                "The employer must give fourteen days' written notice of dismissal.",
                "VERIFIED",
                "smoke",
                null));
    List<CitationCase> citations = adversarial.stream().map(harness::verify).toList();

    assertThat(cases).extracting(AnalysisCase::system).contains("shromikai", "baseline");
    AnalysisCase shromikai =
        cases.stream().filter(c -> c.system().equals("shromikai")).findFirst().orElseThrow();
    assertThat(shromikai.outcome()).isEqualTo("ANALYSED");
    assertThat(shromikai.findings()).isNotEmpty();
    assertThat(shromikai.shown()).allMatch(s -> s.status().equals("VERIFIED"));
    assertThat(shromikai.cloudRequests()).isPositive();
    assertThat(shromikai.canaryHits()).isZero();
    AnalysisCase baseline =
        cases.stream().filter(c -> c.system().equals("baseline")).findFirst().orElseThrow();
    assertThat(baseline.claims())
        .anyMatch(c -> c.reasonCodes().contains("SECTION_NOT_FOUND"))
        .anyMatch(c -> c.status().equals("VERIFIED"));
    assertThat(citations)
        .extracting(CitationCase::actualStatus)
        .containsExactly("REJECTED", "VERIFIED");

    Path run = reports.resolve("run");
    EvalResults.write(run, cases, citations);
    assertThat(EvalGate.check(EvalResults.read(run))).isEmpty();

    EvalReport report =
        ReportAssembler.assemble(
            ReportAssembler.Inputs.forSmoke(EvalResults.read(run), "smoke", "stand-in corpus"));
    ReportGenerator.write(report, reports);
    assertThat(Files.isRegularFile(reports.resolve("report.md"))).isTrue();
    JsonNode json = new ObjectMapper().readTree(reports.resolve("report.json").toFile());
    assertThat(json.get("metrics").get("shromikai").get("M-01").get("count").asLong()).isZero();
    assertThat(json.get("metrics").get("shromikai").get("M-04").get("extra").get("misses").asLong())
        .isZero();
    // Results hold no contract text (R-02): no canary value and no clause sentence.
    String results = Files.readString(run.resolve("results.jsonl"));
    for (String value : SyntheticContracts.allValues("syn-bn-01")) {
      assertThat(results).doesNotContain(value);
    }
  }
}
