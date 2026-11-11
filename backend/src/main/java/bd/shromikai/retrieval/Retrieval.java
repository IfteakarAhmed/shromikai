package bd.shromikai.retrieval;

import java.util.List;

/**
 * The outcome of one retrieval run.
 *
 * @param results search results first (best first), then graph additions, then topic-map additions
 * @param keywordOnly true when the embedding model was unavailable and only full-text search ran
 *     (architecture §5.2 warning {@code retrieval-keyword-only})
 * @param rewritten true when the clause was rewritten into search queries, false when rewriting was
 *     off or fell back to the raw clause
 */
public record Retrieval(List<RetrievalResult> results, boolean keywordOnly, boolean rewritten) {}
