package bd.shromikai.retrieval;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What one retrieval run does. The switches exist so the evaluation can run each ablation row
 * through the same code path as production (FR-23).
 *
 * @param jurisdictions {@code BD} by default, {@code ILO} optional
 * @param limit how many fused search results to return (the application default is 8)
 * @param vector use the vector list
 * @param text use the full-text list
 * @param graph add up to {@code graph-max} sections one hop away
 * @param rewrite rewrite the text into 1 to 3 queries first; only for redacted clauses (FR-21)
 * @param clauseType adds topic-map sections for this clause type when not {@code null}
 */
public record SearchOptions(
    Set<String> jurisdictions,
    int limit,
    boolean vector,
    boolean text,
    boolean graph,
    boolean rewrite,
    @Nullable String clauseType) {

  public static final Set<String> BD = Set.of("BD");

  public SearchOptions {
    if (jurisdictions.isEmpty()) {
      throw new IllegalArgumentException("at least one jurisdiction is required");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }
    jurisdictions = Set.copyOf(jurisdictions);
  }

  /** Hybrid search plus graph expansion on the query as written: free-text law search (FR-22). */
  public static SearchOptions freeText(int limit, Set<String> jurisdictions) {
    return new SearchOptions(jurisdictions, limit, true, true, true, false, null);
  }

  /** Hybrid search plus graph expansion and topic map for a redacted clause, with rewriting. */
  public static SearchOptions forClause(@Nullable String clauseType, Set<String> jurisdictions) {
    return new SearchOptions(jurisdictions, 8, true, true, true, true, clauseType);
  }

  public SearchOptions withVector(boolean value) {
    return new SearchOptions(jurisdictions, limit, value, text, graph, rewrite, clauseType);
  }

  public SearchOptions withText(boolean value) {
    return new SearchOptions(jurisdictions, limit, vector, value, graph, rewrite, clauseType);
  }

  public SearchOptions withGraph(boolean value) {
    return new SearchOptions(jurisdictions, limit, vector, text, value, rewrite, clauseType);
  }

  public SearchOptions withRewrite(boolean value) {
    return new SearchOptions(jurisdictions, limit, vector, text, graph, value, clauseType);
  }
}
