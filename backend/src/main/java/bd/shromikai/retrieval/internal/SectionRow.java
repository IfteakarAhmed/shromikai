package bd.shromikai.retrieval.internal;

import org.jspecify.annotations.Nullable;

/** The columns of one section that a retrieval result needs. */
public record SectionRow(
    long id,
    String sectionKey,
    String actCode,
    String sectionNo,
    @Nullable String headingBn,
    @Nullable String headingEn,
    String snippet,
    String sourceUrl) {}
