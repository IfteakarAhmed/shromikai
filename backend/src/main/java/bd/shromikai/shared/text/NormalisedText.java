package bd.shromikai.shared.text;

/**
 * A text with its untouched {@code display} form and its {@code matching} form. The display form is
 * the very same string that was passed in (FR-14).
 */
public record NormalisedText(String display, String matching) {

  public static NormalisedText of(String display) {
    return new NormalisedText(display, BanglaNormalizer.matchingForm(display));
  }
}
