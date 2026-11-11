package bd.shromikai.privacy;

import bd.shromikai.privacy.internal.Keywords;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The deterministic PII detectors (FR-28, architecture §10.2). Scans text with Bengali digits
 * mapped to Latin digits (a one-to-one mapping, so offsets stay valid for the original text) and
 * returns non-overlapping spans. It never returns or keeps the matched text.
 */
@Component
public final class PiiDetector {

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern PHONE =
      Pattern.compile("(?<!\\d)(?:\\+?880[-\\s]?|0)1[3-9]\\d{2}[-\\s]?\\d{6}(?!\\d)");
  // A digit run; single spaces or hyphens between digit groups are allowed.
  private static final Pattern NUMBER = Pattern.compile("(?<!\\d)\\d+(?:[- ]\\d+)*(?!\\d)");
  private static final Pattern PASSPORT =
      Pattern.compile("(?<![A-Za-z0-9])[A-Z]{1,2}\\d{7}(?!\\d)");
  private static final String MONTHS_EN =
      "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?"
          + "|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?";
  private static final String MONTHS_BN =
      "জানুয়ারি|ফেব্রুয়ারি|মার্চ|এপ্রিল|মে|জুন|জুলাই|আগস্ট|সেপ্টেম্বর|অক্টোবর|নভেম্বর|ডিসেম্বর";
  private static final Pattern DATE =
      Pattern.compile(
          "(?<!\\d)(?:\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}"
              + "|\\d{1,2}(?:st|nd|rd|th)?\\s+(?:"
              + MONTHS_EN
              + "|"
              + MONTHS_BN
              + ")\\.?,?\\s+\\d{4})(?!\\d)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private final Keywords keywords = Keywords.load();

  /** Spans of every detected identifier, sorted by start, never overlapping. */
  public List<PiiMatch> detect(String text) {
    String scan = BanglaNormalizer.toLatinDigits(text);
    List<PiiMatch> found = new ArrayList<>();
    find(EMAIL, scan, PiiType.EMAIL, found);
    Matcher phone = PHONE.matcher(scan);
    while (phone.find()) {
      boolean wallet = keywords.near("wallet", scan, phone.start());
      found.add(
          new PiiMatch(wallet ? PiiType.MOBILE_WALLET : PiiType.PHONE, phone.start(), phone.end()));
    }
    numbers(scan, found);
    passports(scan, found);
    dates(scan, found);
    return resolve(found);
  }

  /** {@code true} if the text contains at least one detectable identifier. */
  public boolean containsPii(String text) {
    return !detect(text).isEmpty();
  }

  private void numbers(String scan, List<PiiMatch> found) {
    Matcher m = NUMBER.matcher(scan);
    while (m.find()) {
      String digits = m.group().replaceAll("[- ]", "");
      int n = digits.length();
      int start = m.start();
      PiiType type = null;
      if (n == 17 && keywords.near("birth_registration", scan, start)) {
        type = PiiType.BIRTH_REG;
      } else if ((n == 10 || n == 13 || n == 17) && keywords.near("nid", scan, start)) {
        type = PiiType.NID;
      } else if (n == 12 && keywords.near("tin", scan, start)) {
        type = PiiType.TIN;
      } else if (n >= 9 && n <= 18 && keywords.near("account", scan, start)) {
        type = PiiType.BANK_ACCOUNT;
      } else if (n >= 13 && n <= 19 && luhn(digits)) {
        type = PiiType.CARD_NUMBER;
      } else if (n == 17) {
        type = PiiType.NID;
      }
      if (type != null) {
        found.add(new PiiMatch(type, start, m.end()));
      }
    }
  }

  private void passports(String scan, List<PiiMatch> found) {
    Matcher m = PASSPORT.matcher(scan);
    while (m.find()) {
      if (keywords.near("passport", scan, m.start())) {
        found.add(new PiiMatch(PiiType.PASSPORT, m.start(), m.end()));
      }
    }
  }

  private void dates(String scan, List<PiiMatch> found) {
    Matcher m = DATE.matcher(scan);
    while (m.find()) {
      if (keywords.near("birth", scan, m.start())) {
        found.add(new PiiMatch(PiiType.DOB, m.start(), m.end()));
      }
    }
  }

  private static void find(Pattern pattern, String scan, PiiType type, List<PiiMatch> out) {
    Matcher m = pattern.matcher(scan);
    while (m.find()) {
      out.add(new PiiMatch(type, m.start(), m.end()));
    }
  }

  /** Keeps the earliest, then the longest, of overlapping spans. */
  private static List<PiiMatch> resolve(List<PiiMatch> found) {
    List<PiiMatch> sorted = new ArrayList<>(found);
    sorted.sort(
        Comparator.comparingInt(PiiMatch::start)
            .thenComparing(Comparator.comparingInt(PiiMatch::length).reversed())
            // The phone pattern is shape only; the NID reading of the same digits needs a keyword
            // ("NID No:") or 17 digits, so it is the better label. Either way the value is
            // replaced.
            .thenComparingInt(m -> m.type() == PiiType.NID ? 0 : 1));
    List<PiiMatch> out = new ArrayList<>();
    int end = -1;
    for (PiiMatch match : sorted) {
      if (match.start() >= end) {
        out.add(match);
        end = match.end();
      }
    }
    return out;
  }

  static boolean luhn(String digits) {
    int sum = 0;
    boolean alternate = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      if (alternate) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      alternate = !alternate;
    }
    return sum % 10 == 0;
  }
}
