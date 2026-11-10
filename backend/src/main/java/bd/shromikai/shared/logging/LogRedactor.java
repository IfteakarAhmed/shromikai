package bd.shromikai.shared.logging;

import java.util.regex.Pattern;

/**
 * Second line of defence for R-02 and FR-03: masks e-mail addresses, Bangladeshi mobile numbers and
 * national ID numbers (Latin and Bangla digits), and replaces any text longer than {@value
 * #MAX_FREE_TEXT} characters. Code must not log such values in the first place.
 */
public final class LogRedactor {

  public static final int MAX_FREE_TEXT = 200;

  private static final String DIGIT = "[0-9০-৯]";

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  /** One optional space or hyphen between digit groups. */
  private static final String SEP = "[\\s-]?";

  /**
   * 01[3-9]XXXXXXXX with an optional +88 / 0088 prefix. A space or hyphen may follow the prefix
   * (also after +880) and may separate any later digit groups: {@code +880 1712-345678}, {@code
   * 0171-234-5678}, {@code 01712 345 678}.
   */
  private static final Pattern MOBILE =
      Pattern.compile(
          "(?<![0-9০-৯])(?:(?:\\+|00|০০)?(?:88|৮৮)"
              + SEP
              + ")?[0০]"
              + SEP
              + "[1১][3-9৩-৯](?:"
              + SEP
              + DIGIT
              + "){8}(?![0-9০-৯])");

  /** NID numbers have 10, 13 or 17 digits. */
  private static final Pattern NID =
      Pattern.compile(
          "(?<![0-9০-৯])(?:" + DIGIT + "{17}|" + DIGIT + "{13}|" + DIGIT + "{10})(?![0-9০-৯])");

  private LogRedactor() {}

  /** E-mail, mobile and NID matches in {@code text}, for scanners that must find, not mask. */
  public static java.util.List<String> piiMatches(String text) {
    java.util.List<String> matches = new java.util.ArrayList<>();
    for (Pattern pattern : java.util.List.of(EMAIL, MOBILE, NID)) {
      pattern.matcher(text).results().map(java.util.regex.MatchResult::group).forEach(matches::add);
    }
    return matches;
  }

  public static String redact(String text) {
    if (text.length() > MAX_FREE_TEXT) {
      return "[REDACTED_TEXT length=" + text.length() + "]";
    }
    String masked = EMAIL.matcher(text).replaceAll("[EMAIL]");
    masked = MOBILE.matcher(masked).replaceAll("[PHONE]");
    return NID.matcher(masked).replaceAll("[NID]");
  }
}
