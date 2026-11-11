package bd.shromikai.verify;

import bd.shromikai.law.SectionKey;
import bd.shromikai.shared.SensitiveText;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A citation proposed by a model, a rule or a reviewer (api.md §5.4). It is never shown to users;
 * only the {@link CitationVerifier} turns it into a {@link VerifiedCitation}. The claim is the
 * legal proposition the section is cited for, with placeholders instead of personal data; it is
 * held as {@link SensitiveText} because a model may have echoed the contract.
 *
 * @param actCode the act the caller says the section belongs to
 * @param provenance required for {@code RULE} and {@code MAPPING}, absent otherwise
 */
public record ClaimedCitation(
    UUID citationId,
    String sectionKey,
    String actCode,
    String sectionNo,
    String quote,
    SensitiveText claim,
    String claimLanguage,
    CitationOrigin origin,
    @Nullable String ruleId,
    @Nullable Provenance provenance) {

  public ClaimedCitation {
    Objects.requireNonNull(citationId, "citationId");
    Objects.requireNonNull(sectionKey, "sectionKey");
    Objects.requireNonNull(quote, "quote");
    Objects.requireNonNull(claim, "claim");
    Objects.requireNonNull(origin, "origin");
    if ((origin == CitationOrigin.RULE || origin == CitationOrigin.MAPPING)
        && (provenance == null || !provenance.twoPersonReviewed())) {
      throw new IllegalArgumentException(
          origin + " citations need an active, two-person-reviewed source (FR-36)");
    }
  }

  /** A claim with act and section number derived from the key; a malformed key keeps blanks. */
  public static ClaimedCitation of(
      String sectionKey,
      String quote,
      SensitiveText claim,
      String claimLanguage,
      CitationOrigin origin,
      @Nullable String ruleId,
      @Nullable Provenance provenance) {
    SectionKey parsed = SectionKey.parse(sectionKey).orElse(null);
    return new ClaimedCitation(
        UUID.randomUUID(),
        sectionKey,
        parsed == null ? "" : parsed.actCode(),
        parsed == null ? "" : parsed.sectionNo(),
        quote,
        claim,
        claimLanguage,
        origin,
        ruleId,
        provenance);
  }

  /** An LLM citation: needs the candidate set and the support model. */
  public static ClaimedCitation llm(
      String sectionKey, String quote, SensitiveText claim, String claimLanguage) {
    return of(sectionKey, quote, claim, claimLanguage, CitationOrigin.LLM, null, null);
  }
}
