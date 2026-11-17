package bd.shromikai.eval;

import java.util.List;
import java.util.Map;

/**
 * Everything a report states (evaluation.md §11 rule 5, §12). Built by {@link ReportAssembler} from
 * results; written by {@link ReportGenerator}.
 *
 * @param metricsBySystem system name → metric ID → result
 */
public record EvalReport(
    Config config,
    Map<String, Map<String, MetricResult>> metricsBySystem,
    List<Ablation> ablations,
    List<String> failures,
    List<String> limitations,
    List<String> knownFailures,
    List<String> errata) {

  /** The configuration block: what ran, on what, with which models, at what cost. */
  public record Config(
      String runId,
      String generatedAt,
      String gitSha,
      boolean gitClean,
      String corpusRelease,
      String set,
      String split,
      List<String> systems,
      int runs,
      List<String> contracts,
      Map<String, String> models,
      Map<String, Integer> promptVersions,
      String verifierIndependence,
      String hardware,
      long liveSpendMicros,
      long ledgerTotalMicros,
      long seed,
      int labelFiles,
      int reviewedLabelFiles,
      int adversarialCases,
      int reviewedAdversarial,
      String dataNote) {}

  /** An ablation row: how it was obtained and its numbers as displayed. */
  public record Ablation(String name, String how, Map<String, String> values) {}
}
