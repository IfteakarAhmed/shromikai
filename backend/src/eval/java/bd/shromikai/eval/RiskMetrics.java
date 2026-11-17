package bd.shromikai.eval;

import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.PredictedFinding;
import bd.shromikai.eval.Labels.GoldRisk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M-06 to M-11 from the matches of §7. Without a reviewer, predictions that match no gold risk stay
 * unjudged: M-07 and M-08 are therefore lower bounds (an unjudged prediction counts as not valid),
 * and the report says so.
 */
public record RiskMetrics(
    MetricResult recall,
    MetricResult precisionLowerBound,
    MetricResult threeRiskCoverage,
    MetricResult nonObviousRecall,
    MetricResult severityAgreement,
    MetricResult correctAbstention) {

  private static final List<String> SEVERITY = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

  /** Over the runs of one system whose contract has labels. */
  public static RiskMetrics of(List<AnalysisCase> runs, Map<String, List<GoldRisk>> labels) {
    long gold = 0;
    long matched = 0;
    long predicted = 0;
    long nonObvious = 0;
    long nonObviousMatched = 0;
    long coveredRuns = 0;
    long labelledRuns = 0;
    long abstainGold = 0;
    long abstained = 0;
    List<int[]> severityPairs = new ArrayList<>();
    for (AnalysisCase run : runs) {
      List<GoldRisk> risks = labels.get(run.contractId());
      if (risks == null || !"ANALYSED".equals(run.outcome())) {
        continue;
      }
      labelledRuns++;
      List<GoldRisk> relevant =
          risks.stream()
              .filter(g -> g.side().equals("BOTH") || g.side().equals(run.side()))
              .toList();
      Map<String, String> pairs = Matching.match(run.findings(), relevant, run.side());
      Map<String, PredictedFinding> byId = new HashMap<>();
      run.findings().forEach(f -> byId.put(f.id(), f));
      gold += relevant.size();
      matched += pairs.size();
      long countable =
          run.findings().stream().filter(f -> !"POLICY_ONLY".equals(f.verification())).count();
      predicted += countable;
      if (pairs.size() >= 3) {
        coveredRuns++;
      }
      for (GoldRisk g : relevant) {
        String p = pairs.get(g.id());
        if (g.nonObvious()) {
          nonObvious++;
          if (p != null) {
            nonObviousMatched++;
          }
        }
        if (p == null) {
          continue;
        }
        PredictedFinding f = byId.get(p);
        severityPairs.add(
            new int[] {SEVERITY.indexOf(g.severity()), SEVERITY.indexOf(f.severity())});
        if (g.acceptableSectionKeys().isEmpty()) {
          abstainGold++;
          if ("UNCERTAIN".equals(f.verification())) {
            abstained++;
          }
        }
      }
    }
    String basis = labelledRuns + " contract-side runs against draft labels";
    long agree = severityPairs.stream().filter(p -> p[0] == p[1]).count();
    int[] a = severityPairs.stream().mapToInt(p -> p[0]).toArray();
    int[] b = severityPairs.stream().mapToInt(p -> p[1]).toArray();
    double kappa = Stats.quadraticKappa(a, b, SEVERITY.size());
    MetricResult nonObviousResult =
        nonObvious == 0
            ? MetricResult.notAvailable(
                "M-09",
                "no gold risk marked non-obvious belongs to a contract that was analysed in this run")
            : MetricResult.proportion("M-09", nonObviousMatched, nonObvious, basis);
    return new RiskMetrics(
        MetricResult.proportion("M-06", matched, gold, basis),
        MetricResult.proportion(
            "M-07",
            matched,
            predicted,
            basis + "; unmatched predictions unjudged (no reviewer)",
            Map.of("unjudged", predicted - matched)),
        MetricResult.proportion("M-08", coveredRuns, labelledRuns, basis + "; matched only"),
        nonObviousResult,
        MetricResult.proportion(
            "M-10",
            agree,
            severityPairs.size(),
            basis,
            Double.isNaN(kappa) ? Map.of() : Map.of("quadraticKappa", MetricResult.round(kappa))),
        abstainGold == 0
            ? MetricResult.notAvailable(
                "M-11", "no matched gold risk without an acceptable section in these runs")
            : MetricResult.proportion("M-11", abstained, abstainGold, basis));
  }
}
