package bd.shromikai.law;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code Act} of api.md §3: the summary fields plus the current sections. */
public record Act(
    String actCode,
    @Nullable String titleBn,
    String titleEn,
    @Nullable Integer year,
    String jurisdiction,
    String authoritativeLang,
    String status,
    @Nullable String ratificationStatus,
    String sourceUrl,
    long sectionCount,
    List<SectionRef> sections) {

  public static Act of(ActSummary s, List<SectionRef> sections) {
    return new Act(
        s.actCode(),
        s.titleBn(),
        s.titleEn(),
        s.year(),
        s.jurisdiction(),
        s.authoritativeLang(),
        s.status(),
        s.ratificationStatus(),
        s.sourceUrl(),
        s.sectionCount(),
        sections);
  }
}
