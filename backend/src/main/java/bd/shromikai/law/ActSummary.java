package bd.shromikai.law;

import org.jspecify.annotations.Nullable;

/** {@code ActSummary} of api.md §3. */
public record ActSummary(
    String actCode,
    @Nullable String titleBn,
    String titleEn,
    @Nullable Integer year,
    String jurisdiction,
    String authoritativeLang,
    String status,
    @Nullable String ratificationStatus,
    String sourceUrl,
    long sectionCount) {}
