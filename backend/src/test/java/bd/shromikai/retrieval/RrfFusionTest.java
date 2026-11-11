package bd.shromikai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.retrieval.RrfFusion.Fused;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-19: known rank lists give a known fused order; ties are broken by section ID. */
@Tag("unit")
@Requirement("FR-19")
class RrfFusionTest {

  @Test
  void shouldRankASectionInBothListsAboveSectionsInOne() {
    List<Fused> fused = RrfFusion.fuse(List.of(List.of(1L, 2L, 3L), List.of(3L, 4L, 5L)), 60, 8);

    assertThat(fused).extracting(Fused::id).containsExactly(3L, 1L, 2L, 4L, 5L);
  }

  @Test
  void shouldScoreEachListAsOneOverKPlusRank() {
    List<Fused> fused = RrfFusion.fuse(List.of(List.of(7L), List.of(9L, 7L)), 60, 8);

    assertThat(fused.get(0).id()).isEqualTo(7L);
    assertThat(fused.get(0).score())
        .isCloseTo(1.0 / 61 + 1.0 / 62, org.assertj.core.data.Offset.offset(1e-12));
    assertThat(fused.get(1).score())
        .isCloseTo(1.0 / 61, org.assertj.core.data.Offset.offset(1e-12));
  }

  @Test
  void shouldBreakTiesByAscendingSectionId() {
    List<Fused> a = RrfFusion.fuse(List.of(List.of(20L, 10L), List.of(10L, 20L)), 60, 8);
    List<Fused> b = RrfFusion.fuse(List.of(List.of(10L, 20L), List.of(20L, 10L)), 60, 8);

    assertThat(a).extracting(Fused::id).containsExactly(10L, 20L);
    assertThat(b).extracting(Fused::id).containsExactly(10L, 20L);
  }

  @Test
  void shouldKeepOnlyTheTopEntries() {
    List<Fused> fused = RrfFusion.fuse(List.of(List.of(1L, 2L, 3L, 4L)), 60, 2);

    assertThat(fused).extracting(Fused::id).containsExactly(1L, 2L);
  }

  @Test
  void shouldReturnNothingForNoLists() {
    assertThat(RrfFusion.fuse(List.of(), 60, 8)).isEmpty();
    assertThat(RrfFusion.fuse(List.of(List.of()), 60, 8)).isEmpty();
  }

  @Test
  void shouldRejectInvalidParameters() {
    assertThatThrownBy(() -> RrfFusion.fuse(List.of(), 0, 8))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RrfFusion.fuse(List.of(), 60, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
