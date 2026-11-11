package bd.shromikai.law;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code Section} of api.md §3: one section version with its authoritative text. */
public record Section(
    String sectionKey,
    String actCode,
    @Nullable String actTitleBn,
    String actTitleEn,
    String sectionNo,
    @Nullable String sectionNoOriginal,
    @Nullable String headingBn,
    @Nullable String headingEn,
    String authoritativeLang,
    String authoritativeText,
    @Nullable Translation translation,
    String versionLabel,
    @Nullable LocalDate effectiveFrom,
    boolean isCurrent,
    String sourceUrl,
    Instant fetchedAt,
    @Nullable String contentHash,
    List<Related> related,
    @Nullable String corpusRelease) {

  /** The text in the act's other language, when the portal published one; never official. */
  public record Translation(String lang, String text, boolean official) {}

  /** An edge from this section to another section. */
  public record Related(String sectionKey, String kind, boolean reviewed) {}
}
