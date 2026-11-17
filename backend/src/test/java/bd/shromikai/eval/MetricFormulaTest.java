package bd.shromikai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.CitationCase;
import bd.shromikai.eval.EvalResults.ClaimRecord;
import bd.shromikai.eval.EvalResults.PredictedFinding;
import bd.shromikai.eval.EvalResults.ShownCitation;
import bd.shromikai.eval.Labels.GoldRisk;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-80: each metric of evaluation.md §6 on toy data with answers computed by hand (the Wilson
 * intervals and the weighted κ were checked with an independent script).
 */
@Tag("unit")
@Requirement("FR-80")
class MetricFormulaTest {

  // ---------------------------------------------------------------- statistics (§10)

  @Test
  void shouldComputeTheWilsonScoreInterval() {
    assertThat(Stats.wilson(55, 55)[0]).isCloseTo(0.9347, within(0.0001));
    assertThat(Stats.wilson(55, 55)[1]).isCloseTo(1.0, within(0.0001));
    assertThat(Stats.wilson(23, 25)[0]).isCloseTo(0.7503, within(0.0001));
    assertThat(Stats.wilson(23, 25)[1]).isCloseTo(0.9778, within(0.0001));
    assertThat(Stats.wilson(0, 10)[1]).isCloseTo(0.2775, within(0.0001));
    assertThat(Stats.wilson(1, 3)[0]).isCloseTo(0.0615, within(0.0001));
  }

  @Test
  void shouldHaveNoIntervalForAnEmptySample() {
    assertThat(Stats.wilson(0, 0)).isNull();
  }

  @Test
  void shouldTakePercentilesByNearestRank() {
    List<Double> values = List.of(15.0, 20.0, 35.0, 40.0, 50.0);
    assertThat(Stats.nearestRank(values, 0.5)).isEqualTo(35.0);
    assertThat(Stats.nearestRank(values, 0.95)).isEqualTo(50.0);
    assertThat(Stats.nearestRank(values, 0.30)).isEqualTo(20.0);
    assertThat(Stats.nearestRank(List.of(7.0), 0.95)).isEqualTo(7.0);
    assertThat(Stats.nearestRank(List.of(), 0.5)).isNaN();
  }

  @Test
  void shouldComputeJaccard() {
    assertThat(Stats.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d"))).isEqualTo(0.5);
    assertThat(Stats.jaccard(Set.of(), Set.of())).isEqualTo(1.0);
    assertThat(Stats.jaccard(Set.of("a"), Set.of())).isEqualTo(0.0);
  }

  @Test
  void shouldComputeQuadraticWeightedKappa() {
    int[] a = {0, 1, 2, 3, 2, 1};
    int[] b = {0, 2, 2, 3, 1, 1};
    assertThat(Stats.quadraticKappa(a, b, 4)).isCloseTo(0.8182, within(0.0001));
    assertThat(Stats.quadraticKappa(new int[] {1, 1}, new int[] {1, 1}, 4)).isNaN();
  }

  @Test
  void shouldFormatEveryProportionWithItsCount() {
    MetricResult r = MetricResult.proportion("M-05", 23, 25, "toy");
    assertThat(r.display()).isEqualTo("0.920 (23 of 25)");
    assertThat(r.ci95()).hasSize(2);
    assertThat(MetricResult.proportion("M-05", 0, 0, "toy").display()).isEqualTo("n = 0");
  }

  // ---------------------------------------------------------------- citation metrics

  private static ShownCitation shown(String id, String status) {
    return new ShownCitation(id, "XX-LAB:" + id + "@original", status);
  }

  private static ClaimRecord claim(String origin, String status, String... reasons) {
    return new ClaimRecord("f1", "XX-LAB:9@original", 1, status, List.of(reasons), origin);
  }

  private static AnalysisCase analysis(
      String contract,
      String system,
      List<PredictedFinding> findings,
      List<ShownCitation> shown,
      List<ClaimRecord> claims) {
    return AnalysisCase.builder(contract, system, "WORKER")
        .findings(findings)
        .shown(shown)
        .claims(claims)
        .build();
  }

  @Test
  void shouldComputeUnverifiedCitationExposure_M01() {
    var ok = analysis("c1", "shromikai", List.of(), List.of(shown("a", "VERIFIED")), List.of());
    var bad =
        analysis(
            "c2",
            "shromikai",
            List.of(),
            List.of(shown("b", "VERIFIED"), shown("c", "UNCERTAIN"), shown("d", "VERIFIED")),
            List.of());

    MetricResult m01 = Metrics.unverifiedExposure(List.of(ok, bad));

    assertThat(m01.count()).isEqualTo(1);
    assertThat(m01.n()).isEqualTo(4);
    assertThat(m01.value()).isEqualTo(0.25);
    assertThat(m01.met()).isFalse();
    assertThat(Metrics.unverifiedExposure(List.of(ok)).met()).isTrue();
  }

  @Test
  void shouldCountShownCitationsOnceByCitationId_M01() {
    var twice =
        analysis(
            "c1",
            "shromikai",
            List.of(),
            List.of(shown("a", "VERIFIED"), shown("a", "VERIFIED")),
            List.of());
    assertThat(Metrics.unverifiedExposure(List.of(twice)).n()).isEqualTo(1);
  }

  @Test
  void shouldComputeTheObjectiveFabricationRate_M03() {
    var base =
        analysis(
            "c1",
            "baseline",
            List.of(),
            List.of(),
            List.of(
                claim("BASELINE", "REJECTED", "SECTION_NOT_FOUND"),
                claim("BASELINE", "REJECTED", "QUOTE_BELOW_THRESHOLD"),
                claim("BASELINE", "UNCERTAIN", "SUPPORT_UNRELATED"),
                claim("BASELINE", "VERIFIED")));

    MetricResult m03 = Metrics.objectiveFabrication(List.of(base), "BASELINE");

    // Section missing and quote not in the section count; an unsupported claim is a judgement.
    assertThat(m03.count()).isEqualTo(2);
    assertThat(m03.n()).isEqualTo(4);
  }

  @Test
  void shouldSayWhatKindOfKeyAnUnresolvedCitationHas_M03() {
    assertThat(Metrics.keyShape("BLA-2006:108@amend-2018")).isEqualTo("statute-key");
    assertThat(Metrics.keyShape("BLA:100-102,108,103@2006"))
        .isEqualTo("malformed-statute-reference");
    assertThat(Metrics.keyShape("CONTRACT:4")).isEqualTo("contract-clause-reference");
    assertThat(Metrics.keyShape("CONTRACT:9@v1")).isEqualTo("contract-clause-reference");
    assertThat(Metrics.keyShape("চুক্তি:৩@v1")).isEqualTo("contract-clause-reference");

    var base =
        analysis(
            "c1",
            "baseline",
            List.of(),
            List.of(),
            List.of(
                new ClaimRecord(
                    "b1", "CONTRACT:4", 1, "REJECTED", List.of("SECTION_NOT_FOUND"), "BASELINE"),
                new ClaimRecord(
                    "b1", "BLA:1-2@2006", 1, "REJECTED", List.of("SECTION_NOT_FOUND"), "BASELINE"),
                new ClaimRecord(
                    "b2",
                    "XX-LAB:99@original",
                    1,
                    "REJECTED",
                    List.of("SECTION_NOT_FOUND"),
                    "BASELINE")));
    MetricResult m03 = Metrics.objectiveFabrication(List.of(base), "BASELINE");
    assertThat(m03.extra())
        .containsEntry(
            "unresolvedByKeyShape",
            java.util.Map.of(
                "contract-clause-reference", 1L,
                "malformed-statute-reference", 1L,
                "statute-key", 1L));
  }

  @Test
  void shouldCountCaughtFabricationsAndMisses_M04() {
    List<CitationCase> adversarial =
        List.of(
            new CitationCase("ac-1", "NONEXISTENT_SECTION", "REJECTED", List.of(), null, false),
            new CitationCase("ac-2", "MISQUOTE", "REJECTED", List.of(), null, false),
            new CitationCase("ac-3", "UNRELATED_CLAIM", "VERIFIED", List.of(), "SUPPORTS", false),
            new CitationCase("ac-4", "VALID_CONTROL", "VERIFIED", List.of(), "SUPPORTS", false));
    var base =
        analysis(
            "c1",
            "baseline",
            List.of(),
            List.of(),
            List.of(claim("BASELINE", "REJECTED", "QUOTE_NOT_FOUND")));

    MetricResult m04 = Metrics.fabricationCatch(adversarial, List.of(base));

    assertThat(m04.n()).isEqualTo(4); // 3 invalid adversarial + 1 objective baseline fabrication
    assertThat(m04.count()).isEqualTo(3);
    assertThat(m04.extra()).containsEntry("misses", 1L);
    assertThat(m04.extra()).containsEntry("missedCases", List.of("ac-3"));
    assertThat(m04.met()).isFalse();
  }

  @Test
  void shouldComputeValidCitationAcceptance_M05() {
    List<CitationCase> cases =
        List.of(
            new CitationCase("v1", "VALID_CONTROL", "VERIFIED", List.of(), "SUPPORTS", false),
            new CitationCase("v2", "VALID_CONTROL", "UNCERTAIN", List.of(), "PARTIAL", false),
            new CitationCase("x", "MISQUOTE", "REJECTED", List.of(), null, false));
    MetricResult m05 = Metrics.validAcceptance(cases);
    assertThat(m05.count()).isEqualTo(1);
    assertThat(m05.n()).isEqualTo(2);
  }

  // ---------------------------------------------------------------- matching (§7) and risks

  private static PredictedFinding finding(
      String id,
      List<String> labels,
      String type,
      String severity,
      String verification,
      double confidence,
      List<String> keys) {
    return new PredictedFinding(
        id, labels, type, "CONFLICT", severity, verification, confidence, keys, "LLM");
  }

  private static GoldRisk gold(
      String id, List<String> labels, String type, String severity, List<String> keys) {
    return new GoldRisk(
        id, labels, type, "CONFLICT", severity, false, "BOTH", keys, !keys.isEmpty());
  }

  @Test
  void shouldMatchOneToOneByConfidence() {
    var g1 = gold("G1", List.of("4"), "NOTICE", "HIGH", List.of("XX-LAB:9@original"));
    var g2 = gold("G2", List.of("5"), "WAGES", "MEDIUM", List.of());
    var p1 =
        finding(
            "f1", List.of("4"), "NOTICE", "HIGH", "VERIFIED", 0.9, List.of("XX-LAB:9@original"));
    var p2 =
        finding("f2", List.of("4"), "NOTICE", "LOW", "VERIFIED", 0.5, List.of("XX-LAB:9@original"));
    var p3 = finding("f3", List.of("5"), "WAGES", "MEDIUM", "UNCERTAIN", 0.7, List.of());
    var p4 =
        finding(
            "f4", List.of("4"), "NOTICE", "HIGH", "VERIFIED", 0.95, List.of("XX-LAB:1@original"));

    Map<String, String> pairs = Matching.match(List.of(p1, p2, p3, p4), List.of(g1, g2), "WORKER");

    // f4 is first by confidence but its verified key is not acceptable; f1 then takes G1.
    assertThat(pairs).containsEntry("G1", "f1").containsEntry("G2", "f3").hasSize(2);
  }

  @Test
  void shouldMatchAnOmissionByTypeWithoutClauses() {
    var g = gold("G1", List.of(), "MATERNITY_BENEFIT", "HIGH", List.of());
    var p = finding("f1", List.of(), "MATERNITY_BENEFIT", "HIGH", "UNCERTAIN", 0.6, List.of());
    var other =
        finding("f2", List.of("3"), "MATERNITY_BENEFIT", "HIGH", "UNCERTAIN", 0.9, List.of());
    assertThat(Matching.match(List.of(other, p), List.of(g), "WORKER")).containsEntry("G1", "f1");
  }

  @Test
  void shouldNotMatchARiskOfTheOtherSide() {
    var g =
        new GoldRisk(
            "G1", List.of("4"), "NOTICE", "CONFLICT", "HIGH", false, "EMPLOYER", List.of(), false);
    var p = finding("f1", List.of("4"), "NOTICE", "HIGH", "UNCERTAIN", 0.9, List.of());
    assertThat(Matching.match(List.of(p), List.of(g), "WORKER")).isEmpty();
  }

  @Test
  void shouldNormaliseClauseLabels() {
    assertThat(Matching.normaliseLabel("৪.")).isEqualTo("৪");
    assertThat(Matching.normaliseLabel(" 2) ")).isEqualTo("2");
    assertThat(Matching.normaliseLabel("ধারা ৫")).isEqualTo("৫");
  }

  @Test
  void shouldComputeRecallPrecisionCoverageSeverityAndAbstention_M06_to_M12() {
    var g1 = gold("G1", List.of("4"), "NOTICE", "HIGH", List.of("XX-LAB:9@original"));
    var g2 = gold("G2", List.of("5"), "WAGES", "MEDIUM", List.of());
    var g3 = gold("G3", List.of("6"), "LEAVE", "LOW", List.of("XX-LAB:3@original"));
    var labels = Map.of("c1", List.of(g1, g2, g3));
    var run =
        analysis(
            "c1",
            "shromikai",
            List.of(
                finding(
                    "f1",
                    List.of("4"),
                    "NOTICE",
                    "HIGH",
                    "VERIFIED",
                    0.9,
                    List.of("XX-LAB:9@original")),
                finding("f2", List.of("5"), "WAGES", "HIGH", "UNCERTAIN", 0.7, List.of()),
                finding(
                    "f3",
                    List.of("8"),
                    "OTHER",
                    "LOW",
                    "VERIFIED",
                    0.4,
                    List.of("XX-LAB:1@original"))),
            List.of(),
            List.of());

    RiskMetrics risks = RiskMetrics.of(List.of(run), labels);

    assertThat(risks.recall().count()).isEqualTo(2); // M-06: G1 and G2 of 3
    assertThat(risks.recall().n()).isEqualTo(3);
    assertThat(risks.precisionLowerBound().count()).isEqualTo(2); // M-07: f3 unjudged
    assertThat(risks.precisionLowerBound().n()).isEqualTo(3);
    assertThat(risks.precisionLowerBound().extra()).containsEntry("unjudged", 1L);
    assertThat(risks.threeRiskCoverage().count()).isEqualTo(0); // M-08: 2 matched < 3
    assertThat(risks.severityAgreement().count()).isEqualTo(1); // M-10: G1 equal, G2 not
    assertThat(risks.severityAgreement().n()).isEqualTo(2);
    assertThat(risks.correctAbstention().count()).isEqualTo(1); // M-11: G2 shown UNCERTAIN
    assertThat(risks.correctAbstention().n()).isEqualTo(1);
    assertThat(risks.nonObviousRecall().status()).isEqualTo(MetricResult.Status.NOT_AVAILABLE);
    MetricResult m12 = Metrics.abstentionRate(List.of(run));
    assertThat(m12.count()).isEqualTo(1);
    assertThat(m12.n()).isEqualTo(3);
  }

  // ---------------------------------------------------------------- segmentation, latency, cost

  @Test
  void shouldComputeBoundaryF1AndTypeAccuracy_M23() {
    var seg =
        Metrics.segmentation(
            List.of(
                new Metrics.ClauseView("1", "A"),
                new Metrics.ClauseView("2", "B"),
                new Metrics.ClauseView("9", "C")),
            List.of(
                new Metrics.ClauseView("1", "A"),
                new Metrics.ClauseView("2", "X"),
                new Metrics.ClauseView("3", "C")));
    // tp 2, fp 1, fn 1: precision 2/3, recall 2/3, F1 2/3; type accuracy 1 of 2 matched.
    assertThat(seg.tp()).isEqualTo(2);
    assertThat(seg.fp()).isEqualTo(1);
    assertThat(seg.fn()).isEqualTo(1);
    assertThat(seg.f1()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(seg.typeCorrect()).isEqualTo(1);
    assertThat(seg.typeMatched()).isEqualTo(2);
  }

  @Test
  void shouldTakeLatencyAndCostPercentilesOverLiveRunsOnly_M19_M20() {
    var a =
        AnalysisCase.builder("c1", "shromikai", "WORKER")
            .latencyMs(40_000)
            .costMicros(400_000)
            .build();
    var b =
        AnalysisCase.builder("c2", "shromikai", "WORKER")
            .latencyMs(60_000)
            .costMicros(500_000)
            .build();
    var replay =
        AnalysisCase.builder("c3", "shromikai", "WORKER")
            .latencyMs(2_000)
            .costMicros(420_000)
            .recorded(true)
            .build();

    MetricResult m19 = Metrics.latency(List.of(a, b, replay));
    MetricResult m20 = Metrics.cost(List.of(a, b, replay));

    assertThat(m19.n()).isEqualTo(2);
    assertThat(m19.extra()).containsEntry("p50Seconds", 40.0).containsEntry("p95Seconds", 60.0);
    // Cost is priced from token usage, so a replayed run's recorded usage still counts.
    assertThat(m20.n()).isEqualTo(3);
    assertThat(m20.extra()).containsEntry("medianUsd", 0.42).containsEntry("p95Usd", 0.5);
  }

  @Test
  void shouldCountCanaryHitsInCloudBoundRequests_M16() {
    var a =
        AnalysisCase.builder("c1", "shromikai", "WORKER").cloudRequests(12).canaryHits(0).build();
    var b = AnalysisCase.builder("c1", "baseline", "WORKER").cloudRequests(1).canaryHits(2).build();
    MetricResult m16 = Metrics.piiEgress(List.of(a, b));
    assertThat(m16.count()).isEqualTo(2);
    assertThat(m16.n()).isEqualTo(13);
    assertThat(m16.met()).isFalse();
  }
}
