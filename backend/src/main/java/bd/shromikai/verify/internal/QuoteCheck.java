package bd.shromikai.verify.internal;

import bd.shromikai.law.Section;
import bd.shromikai.shared.text.QuoteMatcher;
import bd.shromikai.verify.ReasonCode;
import bd.shromikai.verify.VerifyProperties;
import org.jspecify.annotations.Nullable;

/**
 * Check (b), quote match (architecture §7.3, FR-43): the quote has 20 to 300 characters and, after
 * normalisation, is a substring of the authoritative text or reaches the similarity threshold on
 * its best-aligned window. A quote that matches only the translation fails with {@code
 * QUOTE_WRONG_LANGUAGE}. The score is recorded.
 */
public final class QuoteCheck {

  /** The outcome: pass or a reason, the score and the language of the match. */
  public record Outcome(
      boolean passed,
      @Nullable ReasonCode failure,
      @Nullable Double similarity,
      @Nullable String matchedLanguage) {}

  private final VerifyProperties properties;

  public QuoteCheck(VerifyProperties properties) {
    this.properties = properties;
  }

  public Outcome check(String quote, Section section) {
    if (QuoteMatcher.normalisedLength(quote) < properties.quoteMinChars()) {
      return new Outcome(false, ReasonCode.QUOTE_TOO_SHORT, null, null);
    }
    if (quote.strip().length() > properties.quoteMaxChars()) {
      return new Outcome(false, ReasonCode.QUOTE_TOO_LONG, null, null);
    }
    double threshold = properties.quoteSimilarityThreshold();
    double score = QuoteMatcher.similarity(quote, section.authoritativeText());
    if (score >= threshold) {
      return new Outcome(true, null, score, section.authoritativeLang());
    }
    if (section.translation() != null
        && QuoteMatcher.similarity(quote, section.translation().text()) >= threshold) {
      return new Outcome(false, ReasonCode.QUOTE_WRONG_LANGUAGE, score, null);
    }
    ReasonCode reason = score < 0.5 ? ReasonCode.QUOTE_NOT_FOUND : ReasonCode.QUOTE_BELOW_THRESHOLD;
    return new Outcome(false, reason, score, null);
  }
}
