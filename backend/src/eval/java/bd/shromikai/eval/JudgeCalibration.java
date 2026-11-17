package bd.shromikai.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * The gate of evaluation.md §9.5: the clarity judge is used only when Spearman ρ against the mean
 * human score is at least 0.60 and at least 8 of 10 judge scores are within ±1 of it. Without human
 * ratings there is nothing to calibrate against, and M-21 is reported as not done.
 */
public final class JudgeCalibration {

  static final double MIN_SPEARMAN = 0.60;
  static final int MIN_WITHIN_ONE = 8;

  private JudgeCalibration() {}

  /** The decision with its numbers. */
  public record Decision(boolean useJudge, String reason, double spearman, int withinOne) {

    /** M-21 as it must be reported when the judge is not used and no human rated the memos. */
    public MetricResult clarityMetric() {
      return MetricResult.notDone(
          "M-21",
          "LLM-as-judge not used ("
              + reason
              + "); no human clarity ratings exist, so M-21 is not"
              + " measured");
    }
  }

  public static Decision decide(List<Double> human, List<Double> judge) {
    if (human.isEmpty() || judge.isEmpty()) {
      return new Decision(false, "no human ratings", Double.NaN, 0);
    }
    if (human.size() != judge.size()) {
      throw new IllegalArgumentException("human and judge ratings differ in number");
    }
    double rho = spearman(human, judge);
    int within = 0;
    for (int i = 0; i < human.size(); i++) {
      if (Math.abs(human.get(i) - judge.get(i)) <= 1.0) {
        within++;
      }
    }
    boolean ok = rho >= MIN_SPEARMAN && within >= MIN_WITHIN_ONE;
    return new Decision(
        ok,
        ok
            ? "agreement passes both thresholds"
            : String.format(
                java.util.Locale.ROOT,
                "agreement below threshold (rho %.2f, %d of %d within 1)",
                rho,
                within,
                human.size()),
        rho,
        within);
  }

  /** Spearman's ρ: Pearson correlation of average ranks (ties share the mean rank). */
  public static double spearman(List<Double> a, List<Double> b) {
    double[] ra = ranks(a);
    double[] rb = ranks(b);
    double ma = mean(ra);
    double mb = mean(rb);
    double cov = 0;
    double va = 0;
    double vb = 0;
    for (int i = 0; i < ra.length; i++) {
      cov += (ra[i] - ma) * (rb[i] - mb);
      va += (ra[i] - ma) * (ra[i] - ma);
      vb += (rb[i] - mb) * (rb[i] - mb);
    }
    return va == 0 || vb == 0 ? Double.NaN : cov / Math.sqrt(va * vb);
  }

  private static double mean(double[] v) {
    double s = 0;
    for (double x : v) {
      s += x;
    }
    return s / v.length;
  }

  private static double[] ranks(List<Double> values) {
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      order.add(i);
    }
    order.sort((x, y) -> Double.compare(values.get(x), values.get(y)));
    double[] ranks = new double[values.size()];
    int i = 0;
    while (i < order.size()) {
      int j = i;
      while (j + 1 < order.size()
          && values.get(order.get(j + 1)).equals(values.get(order.get(i)))) {
        j++;
      }
      double rank = (i + j) / 2.0 + 1;
      for (int k = i; k <= j; k++) {
        ranks[order.get(k)] = rank;
      }
      i = j + 1;
    }
    return ranks;
  }
}
