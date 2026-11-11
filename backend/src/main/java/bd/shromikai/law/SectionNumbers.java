package bd.shromikai.law;

import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Section numbers as printed in the sources (Bangla or Latin digits, optional letter suffix such as
 * {@code ৩ক} or {@code 27A}, optional range such as {@code 76-123}) and their Latin key form
 * (ADR-0011 decision 5). Bangla suffix letters map to Latin letters in the alphabetical order of
 * the Bangla consonants ({@code ক} to {@code A}, {@code খ} to {@code B}, ...). The mapping only
 * encodes the key; the original form is stored beside it.
 */
public final class SectionNumbers {

  private static final String BANGLA_CONSONANTS = "কখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহ";
  private static final String PART = "[0-9০-৯]+[A-Za-zক-হ]*";

  /** A whole printed number: one part, or two parts joined by a hyphen. */
  public static final Pattern NUMBER = Pattern.compile("^" + PART + "(?:-" + PART + ")?$");

  /** A printed number followed by a full stop (period, danda or U+09F7) at the start of a title. */
  public static final Pattern LEADING_NUMBER =
      Pattern.compile("^\\s*(" + PART + "(?:-" + PART + ")?)\\s*[.।৷]");

  private SectionNumbers() {}

  /** Latin key form of a printed number, or empty when it is not a section number. */
  public static Optional<String> toLatin(String printed) {
    String trimmed = printed.strip();
    if (!NUMBER.matcher(trimmed).matches()) {
      return Optional.empty();
    }
    StringBuilder out = new StringBuilder();
    for (char c : BanglaNormalizer.toLatinDigits(trimmed).toCharArray()) {
      int index = BANGLA_CONSONANTS.indexOf(c);
      if (index >= 0) {
        if (index >= 26) {
          return Optional.empty();
        }
        out.append((char) ('A' + index));
      } else {
        out.append(c);
      }
    }
    return Optional.of(out.toString());
  }

  /** The printed number at the start of {@code title}, if there is one. */
  public static Optional<String> leadingNumber(String title) {
    Matcher matcher = LEADING_NUMBER.matcher(title);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  /** {@code title} without its leading number and full stop. */
  public static String withoutLeadingNumber(String title) {
    Matcher matcher = LEADING_NUMBER.matcher(title);
    return matcher.find() ? title.substring(matcher.end()).strip() : title.strip();
  }
}
