package bd.shromikai.retrieval;

import java.util.List;
import java.util.Optional;

/**
 * Turns a <em>redacted</em> clause into 1 to 3 short search queries in Bangla and English (FR-21).
 * Never called for free-text law search (ADR-0006).
 */
public interface QueryRewriter {

  /**
   * @return the queries, or empty when rewriting failed and the raw clause must be used
   */
  Optional<List<String>> rewrite(String redactedClause);
}
