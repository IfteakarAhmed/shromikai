package bd.shromikai.retrieval.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Graph expansion (architecture §8.2 step 5): one hop along {@code USES_DEFINITION} and {@code
 * REFERS_TO} from the fused top sections, at most {@code graph-max} added sections, no duplicates.
 *
 * <p>Definitions come before cross-references, and within a kind the section reached from the
 * better-ranked seed comes first, then the lower section ID. (The SQL sketch in §8.2 orders by the
 * kind name, which would put {@code REFERS_TO} first and crowd out definitions; FR-20 wants the
 * definitions pulled in.)
 */
@Component
public class GraphExpander {

  private final SectionSearchRepository repository;
  private final int max;

  GraphExpander(SectionSearchRepository repository, RetrievalProperties properties) {
    this.repository = repository;
    this.max = properties.graphMax();
  }

  /**
   * @param seedIds the fused top sections, best first
   * @return section IDs to add as context, in order, at most {@code graph-max}
   */
  public List<Long> expand(List<Long> seedIds) {
    return order(seedIds, repository.edgesFrom(seedIds), max);
  }

  static List<Long> order(List<Long> seedIds, List<GraphEdge> edges, int max) {
    Set<Long> seeds = new LinkedHashSet<>(seedIds);
    Comparator<GraphEdge> byPriority =
        Comparator.comparingInt((GraphEdge e) -> "USES_DEFINITION".equals(e.kind()) ? 0 : 1)
            .thenComparingInt(e -> seedIds.indexOf(e.fromId()))
            .thenComparingLong(GraphEdge::toId);
    Set<Long> added = new LinkedHashSet<>();
    List<GraphEdge> sorted = new ArrayList<>(edges);
    sorted.sort(byPriority);
    for (GraphEdge edge : sorted) {
      if (added.size() >= max) {
        break;
      }
      if (!seeds.contains(edge.toId())) {
        added.add(edge.toId());
      }
    }
    return List.copyOf(added);
  }
}
