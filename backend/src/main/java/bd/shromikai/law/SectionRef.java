package bd.shromikai.law;

import org.jspecify.annotations.Nullable;

/** One current section in an act's table of contents. */
public record SectionRef(
    String sectionKey,
    String sectionNo,
    @Nullable String sectionNoOriginal,
    @Nullable String headingBn,
    @Nullable String headingEn) {}
