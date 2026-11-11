package bd.shromikai.verify;

import bd.shromikai.law.Section;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The only citation shape in user-facing output (api.md §5.7, R-01). The constructor is
 * package-private, so only {@link CitationVerifier} can create one, and only for a citation whose
 * status is {@code VERIFIED}; ArchUnit enforces that no other module calls it. The quote shown is
 * the text stored in the corpus, never the model's copy.
 */
public final class VerifiedCitation {

  private final String citationId;
  private final String sectionKey;
  private final String actCode;
  private final @Nullable String actTitleBn;
  private final @Nullable String actTitleEn;
  private final String sectionNo;
  private final @Nullable String headingBn;
  private final @Nullable String headingEn;
  private final String quote;
  private final String quoteLanguage;
  private final @Nullable Translation translation;
  private final String sourceUrl;
  private final String versionLabel;
  private final @Nullable LocalDate effectiveFrom;
  private final VerificationResult verification;

  /** The section's text in the other language, when stored; never official (api.md §5.7). */
  public record Translation(String lang, String text, boolean official) {}

  VerifiedCitation(Section section, String storedQuote, VerificationResult verification) {
    if (verification.status() != VerificationStatus.VERIFIED) {
      throw new IllegalArgumentException("Only a VERIFIED citation can be shown (R-01)");
    }
    this.citationId = verification.citationId().toString();
    this.sectionKey = section.sectionKey();
    this.actCode = section.actCode();
    this.actTitleBn = section.actTitleBn();
    this.actTitleEn = section.actTitleEn();
    this.sectionNo = section.sectionNo();
    this.headingBn = section.headingBn();
    this.headingEn = section.headingEn();
    this.quote = storedQuote;
    this.quoteLanguage = section.authoritativeLang();
    this.translation =
        section.translation() == null
            ? null
            : new Translation(
                section.translation().lang(),
                section.translation().text(),
                section.translation().official());
    this.sourceUrl = section.sourceUrl();
    this.versionLabel = section.versionLabel();
    this.effectiveFrom = section.effectiveFrom();
    this.verification = verification;
  }

  @JsonProperty("citationId")
  public String citationId() {
    return citationId;
  }

  @JsonProperty("sectionKey")
  public String sectionKey() {
    return sectionKey;
  }

  @JsonProperty("actCode")
  public String actCode() {
    return actCode;
  }

  @JsonProperty("actTitleBn")
  public @Nullable String actTitleBn() {
    return actTitleBn;
  }

  @JsonProperty("actTitleEn")
  public @Nullable String actTitleEn() {
    return actTitleEn;
  }

  @JsonProperty("sectionNo")
  public String sectionNo() {
    return sectionNo;
  }

  @JsonProperty("headingBn")
  public @Nullable String headingBn() {
    return headingBn;
  }

  @JsonProperty("headingEn")
  public @Nullable String headingEn() {
    return headingEn;
  }

  @JsonProperty("quote")
  public String quote() {
    return quote;
  }

  @JsonProperty("quoteLanguage")
  public String quoteLanguage() {
    return quoteLanguage;
  }

  @JsonProperty("translation")
  public @Nullable Translation translation() {
    return translation;
  }

  @JsonProperty("sourceUrl")
  public String sourceUrl() {
    return sourceUrl;
  }

  @JsonProperty("versionLabel")
  public String versionLabel() {
    return versionLabel;
  }

  @JsonProperty("effectiveFrom")
  public @Nullable LocalDate effectiveFrom() {
    return effectiveFrom;
  }

  @JsonProperty("verification")
  public VerificationResult verification() {
    return verification;
  }

  @Override
  public String toString() {
    return "VerifiedCitation[" + sectionKey + ", " + verification.status() + "]";
  }
}
