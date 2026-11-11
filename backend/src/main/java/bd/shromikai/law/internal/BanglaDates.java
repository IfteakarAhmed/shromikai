package bd.shromikai.law.internal;

import bd.shromikai.shared.text.BanglaNormalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Publication dates as printed by the portal, in Bangla ("১১ অক্টোবর ২০০৬") or English. */
final class BanglaDates {

  private static final Map<String, Integer> MONTHS =
      Map.ofEntries(
          Map.entry("জানুয়ারি", 1),
          Map.entry("জানুয়ারী", 1),
          Map.entry("ফেব্রুয়ারি", 2),
          Map.entry("ফেব্রুয়ারী", 2),
          Map.entry("মার্চ", 3),
          Map.entry("এপ্রিল", 4),
          Map.entry("মে", 5),
          Map.entry("জুন", 6),
          Map.entry("জুলাই", 7),
          Map.entry("আগস্ট", 8),
          Map.entry("সেপ্টেম্বর", 9),
          Map.entry("অক্টোবর", 10),
          Map.entry("নভেম্বর", 11),
          Map.entry("ডিসেম্বর", 12),
          Map.entry("january", 1),
          Map.entry("february", 2),
          Map.entry("march", 3),
          Map.entry("april", 4),
          Map.entry("may", 5),
          Map.entry("june", 6),
          Map.entry("july", 7),
          Map.entry("august", 8),
          Map.entry("september", 9),
          Map.entry("october", 10),
          Map.entry("november", 11),
          Map.entry("december", 12));

  private static final Pattern DATE =
      Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+([\\p{L}\\p{M}]+),?\\s+(\\d{4})");

  private BanglaDates() {}

  /** The first date found in {@code text}, or empty when there is none. */
  static Optional<LocalDate> parse(String text) {
    String latin = BanglaNormalizer.toLatinDigits(text);
    Matcher matcher = DATE.matcher(latin);
    while (matcher.find()) {
      Integer month = MONTHS.get(matcher.group(2).toLowerCase(Locale.ROOT));
      if (month != null) {
        try {
          return Optional.of(
              LocalDate.of(
                  Integer.parseInt(matcher.group(3)), month, Integer.parseInt(matcher.group(1))));
        } catch (java.time.DateTimeException ignored) {
          // not a real date: keep looking
        }
      }
    }
    return Optional.empty();
  }
}
