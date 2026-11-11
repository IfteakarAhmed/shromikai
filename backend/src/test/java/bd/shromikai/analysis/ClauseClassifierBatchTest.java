package bd.shromikai.analysis;

import static bd.shromikai.analysis.AnalysisFixtures.clause;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-33, ADR-0019: a classify call holds at most ten clauses, so its answer fits the output cap
 * whatever the length of the document; documents of up to twenty clauses keep their two batches.
 */
@Tag("unit")
@Requirement("FR-33")
class ClauseClassifierBatchTest {

  private static List<Clause> clauses(int n) {
    List<Clause> out = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      out.add(clause("c" + i, ClauseType.OTHER, "Clause " + i + "."));
    }
    return out;
  }

  @Test
  void shouldKeepTwoBatchesUpToTwentyClauses() {
    assertThat(ClauseClassifier.batches(clauses(10))).extracting(List::size).containsExactly(5, 5);
    assertThat(ClauseClassifier.batches(clauses(20)))
        .extracting(List::size)
        .containsExactly(10, 10);
    assertThat(ClauseClassifier.batches(clauses(1))).extracting(List::size).containsExactly(1);
  }

  @Test
  void shouldSplitALongDocumentIntoBatchesOfAtMostTen() {
    List<List<Clause>> batches = ClauseClassifier.batches(clauses(47));

    assertThat(batches).hasSize(5);
    assertThat(batches).allMatch(b -> b.size() <= ClauseClassifier.MAX_PER_BATCH);
    assertThat(batches.stream().mapToInt(List::size).sum()).isEqualTo(47);
  }
}
