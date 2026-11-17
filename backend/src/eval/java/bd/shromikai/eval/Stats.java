package bd.shromikai.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The statistics of evaluation.md §10: Wilson intervals, nearest-rank percentiles, Jaccard, κ. */
public final class Stats {

  /** z for a two-sided 95 % interval. */
  private static final double Z = 1.959963984540054;

  private Stats() {}

  /** The 95 % Wilson score interval of {@code k} successes in {@code n}; {@code null} for n = 0. */
  public static double @Nullable [] wilson(long k, long n) {
    if (n <= 0) {
      return null;
    }
    double p = (double) k / n;
    double z2 = Z * Z;
    double denominator = 1 + z2 / n;
    double centre = p + z2 / (2.0 * n);
    double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
    return new double[] {
      Math.max(0, (centre - half) / denominator), Math.min(1, (centre + half) / denominator)
    };
  }

  /** The nearest-rank percentile ({@code p} in (0, 1]); NaN for no values. */
  public static double nearestRank(List<Double> values, double p) {
    if (values.isEmpty()) {
      return Double.NaN;
    }
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int rank = (int) Math.ceil(p * sorted.size());
    return sorted.get(Math.max(1, Math.min(rank, sorted.size())) - 1);
  }

  /** |A ∩ B| ÷ |A ∪ B|; two empty sets are identical (1.0). */
  public static <T> double jaccard(Set<T> a, Set<T> b) {
    if (a.isEmpty() && b.isEmpty()) {
      return 1.0;
    }
    Set<T> union = new HashSet<>(a);
    union.addAll(b);
    Set<T> both = new HashSet<>(a);
    both.retainAll(b);
    return (double) both.size() / union.size();
  }

  /** Quadratic-weighted Cohen's κ over ordinal ratings 0..categories-1; NaN when undefined. */
  public static double quadraticKappa(int[] a, int[] b, int categories) {
    if (a.length != b.length || a.length == 0) {
      return Double.NaN;
    }
    int n = a.length;
    double[][] observed = new double[categories][categories];
    double[] rows = new double[categories];
    double[] cols = new double[categories];
    for (int i = 0; i < n; i++) {
      observed[a[i]][b[i]]++;
      rows[a[i]]++;
      cols[b[i]]++;
    }
    double num = 0;
    double den = 0;
    double scale = (double) (categories - 1) * (categories - 1);
    for (int i = 0; i < categories; i++) {
      for (int j = 0; j < categories; j++) {
        double w = (i - j) * (i - j) / scale;
        num += w * observed[i][j];
        den += w * rows[i] * cols[j] / n;
      }
    }
    return den == 0 ? Double.NaN : 1 - num / den;
  }
}
