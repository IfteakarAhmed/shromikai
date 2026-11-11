package bd.shromikai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.retrieval.RetrievalMetrics.Outcome;
import bd.shromikai.retrieval.RetrievalMetrics.Summary;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-23: hit@5, MRR@10 and recall@8 (evaluation.md M-15). */
@Tag("unit")
@Requirement("FR-23")
class RetrievalMetricsTest {

  private static Outcome outcome(List<String> ranked, String... expected) {
    return new Outcome(ranked, Set.of(expected));
  }

  @Test
  void shouldCountAHitOnlyWithinTheFirstFive() {
    List<String> ranked = List.of("a", "b", "c", "d", "e", "f", "g");

    assertThat(RetrievalMetrics.hitAt(5, outcome(ranked, "e"))).isTrue();
    assertThat(RetrievalMetrics.hitAt(5, outcome(ranked, "f"))).isFalse();
  }

  @Test
  void shouldUseTheReciprocalRankOfTheFirstExpectedKeyWithinTen() {
    List<String> ranked = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");

    assertThat(RetrievalMetrics.reciprocalRank(10, outcome(ranked, "c", "a"))).isEqualTo(1.0);
    assertThat(RetrievalMetrics.reciprocalRank(10, outcome(ranked, "d"))).isEqualTo(0.25);
    assertThat(RetrievalMetrics.reciprocalRank(10, outcome(ranked, "k"))).isZero();
  }

  @Test
  void shouldMeasureRecallOverTheWholeFinalCandidateList() {
    Outcome o = outcome(List.of("a", "b", "x"), "a", "x", "missing", "gone");

    assertThat(RetrievalMetrics.recall(o)).isEqualTo(0.5);
  }

  @Test
  void shouldSummariseWithCounts() {
    Summary summary =
        RetrievalMetrics.summarise(
            List.of(
                outcome(List.of("a", "b"), "a"),
                outcome(List.of("a", "b"), "b"),
                outcome(List.of("a", "b"), "z"),
                outcome(List.of("z"), "z")));

    assertThat(summary.questions()).isEqualTo(4);
    assertThat(summary.hitsAt5()).isEqualTo(3);
    assertThat(summary.hitAt5()).isEqualTo(0.75);
    assertThat(summary.mrrAt10()).isEqualTo((1.0 + 0.5 + 0.0 + 1.0) / 4);
    assertThat(summary.recallAt8()).isEqualTo(0.75);
  }

  @Test
  void shouldSummariseNoQuestionsAsZeros() {
    assertThat(RetrievalMetrics.summarise(List.of()).questions()).isZero();
  }

  @Test
  void shouldRejectAQuestionWithoutExpectedKeys() {
    assertThatThrownBy(() -> new Outcome(List.of("a"), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
