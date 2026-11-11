package bd.shromikai.retrieval;

import org.jspecify.annotations.Nullable;

/**
 * One retrieved section with the scores that explain why it was retrieved (FR-22).
 *
 * @param snippet first 300 characters of the section text (statute text, not contract text)
 * @param vectorRank 1-based rank in the vector list, {@code null} if absent from it
 * @param textRank 1-based rank in the full-text list, {@code null} if absent from it
 * @param fusedScore reciprocal-rank-fusion score; {@code 0} for {@code GRAPH} and {@code TOPIC_MAP}
 */
public record RetrievalResult(
    String sectionKey,
    String actCode,
    String sectionNo,
    @Nullable String headingBn,
    @Nullable String headingEn,
    String snippet,
    String sourceUrl,
    @Nullable Integer vectorRank,
    @Nullable Integer textRank,
    double fusedScore,
    RetrievalOrigin origin) {}
