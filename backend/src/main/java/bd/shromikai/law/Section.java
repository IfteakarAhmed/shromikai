package bd.shromikai.law;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code Section} of api.md §3: one section version with its authoritative text. */
@Schema(name = "Section")
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

  /** The same section with its related sections filled in. */
  public Section withRelated(List<Related> newRelated) {
    return new Section(
        sectionKey,
        actCode,
        actTitleBn,
        actTitleEn,
        sectionNo,
        sectionNoOriginal,
        headingBn,
        headingEn,
        authoritativeLang,
        authoritativeText,
        translation,
        versionLabel,
        effectiveFrom,
        isCurrent,
        sourceUrl,
        fetchedAt,
        contentHash,
        newRelated,
        corpusRelease);
  }

  /** The text in the act's other language, when the portal published one; never official. */
  @Schema(name = "Translation")
  public record Translation(String lang, String text, boolean official) {}

  /** An edge from this section to another section. */
  @Schema(name = "Related")
  public record Related(String sectionKey, String kind, boolean reviewed) {}
}
