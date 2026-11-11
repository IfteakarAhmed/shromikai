package bd.shromikai.retrieval;

import java.util.List;
import java.util.Set;

/**
 * Retrieval quality metrics M-15 (evaluation.md §6): hit@5, MRR@10 and recall@8. One implementation
 * shared by the P4 eval runner and the P11 {@code RetrievalSmokeService}.
 */
public final class RetrievalMetrics {

  /**
   * The result of one question.
   *
   * @param ranked section keys of the final candidate list, best first (search results, then graph
   *     and topic-map additions)
   * @param expected the expected section keys; must not be empty
   */
  public record Outcome(List<String> ranked, Set<String> expected) {
    public Outcome {
      if (expected.isEmpty()) {
        throw new IllegalArgumentException("a question needs at least one expected section key");
      }
      ranked = List.copyOf(ranked);
      expected = Set.copyOf(expected);
    }
  }

  /** Aggregate over questions; counts sit beside the ratios (R-07). */
  public record Summary(
      int questions, int hitsAt5, double mrrAt10, double recallAt8, double hitAt5) {}

  private RetrievalMetrics() {}

  /** Whether any expected key is among the first {@code k} ranked keys. */
  public static boolean hitAt(int k, Outcome outcome) {
    return firstRank(k, outcome) > 0;
  }

  /** 1 ÷ rank of the first expected key within the first {@code k}; 0 if none. */
  public static double reciprocalRank(int k, Outcome outcome) {
    int rank = firstRank(k, outcome);
    return rank == 0 ? 0.0 : 1.0 / rank;
  }

  /** Share of expected keys that appear anywhere in the final candidate list. */
  public static double recall(Outcome outcome) {
    long found = outcome.expected().stream().filter(outcome.ranked()::contains).count();
    return (double) found / outcome.expected().size();
  }

  public static Summary summarise(List<Outcome> outcomes) {
    int n = outcomes.size();
    if (n == 0) {
      return new Summary(0, 0, 0, 0, 0);
    }
    int hits = (int) outcomes.stream().filter(o -> hitAt(5, o)).count();
    double mrr = outcomes.stream().mapToDouble(o -> reciprocalRank(10, o)).sum() / n;
    double recall = outcomes.stream().mapToDouble(RetrievalMetrics::recall).sum() / n;
    return new Summary(n, hits, mrr, recall, (double) hits / n);
  }

  private static int firstRank(int k, Outcome outcome) {
    List<String> ranked = outcome.ranked();
    for (int i = 0; i < Math.min(k, ranked.size()); i++) {
      if (outcome.expected().contains(ranked.get(i))) {
        return i + 1;
      }
    }
    return 0;
  }
}
