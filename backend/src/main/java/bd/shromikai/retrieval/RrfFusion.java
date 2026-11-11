package bd.shromikai.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal-rank fusion (architecture §8.2 step 4): {@code score(s) = Σ 1 ÷ (k + rank(s))} over
 * every ranked list that contains {@code s}, ranks starting at 1. Ties are broken by ascending
 * section ID so the order is the same on every run (FR-19).
 */
public final class RrfFusion {

  /** Scores closer than 1e-12 count as equal, so summation order cannot reorder a tie. */
  private static final double SCALE = 1e12;

  public record Fused(long id, double score) {}

  private RrfFusion() {}

  /**
   * @param rankedLists each list holds section IDs, best first; a list must not repeat an ID
   * @param k the RRF constant (60)
   * @param limit how many fused entries to return
   */
  public static List<Fused> fuse(List<List<Long>> rankedLists, int k, int limit) {
    if (k < 1 || limit < 0) {
      throw new IllegalArgumentException("k must be positive and limit not negative");
    }
    Map<Long, Double> scores = new HashMap<>();
    for (List<Long> list : rankedLists) {
      for (int i = 0; i < list.size(); i++) {
        scores.merge(list.get(i), 1.0 / (k + i + 1), Double::sum);
      }
    }
    List<Fused> fused = new ArrayList<>(scores.size());
    scores.forEach((id, score) -> fused.add(new Fused(id, score)));
    fused.sort(
        Comparator.comparingLong((Fused f) -> -Math.round(f.score() * SCALE))
            .thenComparingLong(Fused::id));
    return List.copyOf(fused.subList(0, Math.min(limit, fused.size())));
  }
}
