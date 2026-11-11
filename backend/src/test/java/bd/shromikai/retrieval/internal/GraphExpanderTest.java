package bd.shromikai.retrieval.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-20: ordering and cap of graph extras. */
@Tag("unit")
@Requirement("FR-20")
class GraphExpanderTest {

  @Test
  void shouldOrderDefinitionsBeforeReferencesThenBySeedRankThenId() {
    List<GraphEdge> edges =
        List.of(
            new GraphEdge(2, 50, "REFERS_TO"),
            new GraphEdge(1, 60, "REFERS_TO"),
            new GraphEdge(1, 55, "REFERS_TO"),
            new GraphEdge(2, 70, "USES_DEFINITION"));

    assertThat(GraphExpander.order(List.of(1L, 2L), edges, 4)).containsExactly(70L, 55L, 60L, 50L);
  }

  @Test
  void shouldCapAtMaxAndDropSeedsAndDuplicates() {
    List<GraphEdge> edges =
        List.of(
            new GraphEdge(1, 10, "REFERS_TO"),
            new GraphEdge(2, 10, "USES_DEFINITION"),
            new GraphEdge(1, 2, "REFERS_TO"),
            new GraphEdge(1, 11, "REFERS_TO"),
            new GraphEdge(1, 12, "REFERS_TO"));

    assertThat(GraphExpander.order(List.of(1L, 2L), edges, 2)).containsExactly(10L, 11L);
  }

  @Test
  void shouldReturnNothingWhenMaxIsZero() {
    assertThat(GraphExpander.order(List.of(1L), List.of(new GraphEdge(1, 9, "REFERS_TO")), 0))
        .isEmpty();
  }
}
