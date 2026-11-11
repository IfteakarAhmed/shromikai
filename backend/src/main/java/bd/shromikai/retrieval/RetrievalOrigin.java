package bd.shromikai.retrieval;

/** Why a section is in a result list (api.md §6, {@code RetrievalOrigin}). */
public enum RetrievalOrigin {
  /** Found by vector or full-text search and ranked by reciprocal-rank fusion. */
  SEARCH,
  /** Pulled in as context by one hop along {@code USES_DEFINITION} or {@code REFERS_TO}. */
  GRAPH,
  /** Added from the curated topic map for the clause's type. */
  TOPIC_MAP
}
