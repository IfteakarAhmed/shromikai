package bd.shromikai.analysis;

import bd.shromikai.rules.ClauseFact;
import bd.shromikai.rules.Unit;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex cross-check of the model's facts (FR-35): finds numbers that sit next to a unit word, in
 * English or Bangla, and says whether one of them equals a stated fact. It reads numbers only; it
 * decides nothing about what a clause means.
 */
final class RegexFacts {

  /** The unit families a number with a unit word can belong to. */
  enum Family {
    DAYS,
    MONTHS,
    HOURS,
    PERCENT,
    MULTIPLIER,
    MONEY
  }

  /** A number found next to a unit word; {@code days} are canonical (weeks times seven). */
  record Found(Family family, double value) {}

  private static final String NUMBER = "(\\d+(?:\\.\\d+)?)";
  private static final String BRACKET = "(?:\\s*\\([^)]{0,30}\\))?";

  private static final Pattern TIME_AND_HOURS =
      Pattern.compile(
          NUMBER
              + BRACKET
              + "[\\s-]*(?:(?:working|calendar|কর্ম)\\s*)?"
              + "(days?|day|দিন|weeks?|সপ্তাহ|months?|মাস|hours?|hrs?|ঘণ্টা|ঘন্টা|%|percent|per\\s?cent|শতাংশ|times|x|গুণ)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern MONEY_BEFORE =
      Pattern.compile(
          "(?:bdt|tk\\.?|taka|৳)\\s*" + NUMBER, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern MONEY_AFTER =
      Pattern.compile(
          NUMBER + "\\s*(?:bdt|tk\\b|taka|টাকা)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern DOUBLE =
      Pattern.compile(
          "\\b(twice|double)\\b|দ্বিগুণ", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private RegexFacts() {}

  static List<Found> find(String clauseText) {
    String text = BanglaNormalizer.toLatinDigits(clauseText).replaceAll("(?<=\\d),(?=\\d{3})", "");
    List<Found> found = new ArrayList<>();
    Matcher m = TIME_AND_HOURS.matcher(text);
    while (m.find()) {
      double value = Double.parseDouble(m.group(1));
      String unit = m.group(2).toLowerCase(java.util.Locale.ROOT);
      if (unit.startsWith("day") || unit.equals("দিন")) {
        found.add(new Found(Family.DAYS, value));
      } else if (unit.startsWith("week") || unit.equals("সপ্তাহ")) {
        found.add(new Found(Family.DAYS, value * 7));
      } else if (unit.startsWith("month") || unit.equals("মাস")) {
        found.add(new Found(Family.MONTHS, value));
      } else if (unit.startsWith("h") || unit.startsWith("ঘ")) {
        found.add(new Found(Family.HOURS, value));
      } else if (unit.equals("times") || unit.equals("x") || unit.equals("গুণ")) {
        found.add(new Found(Family.MULTIPLIER, value));
      } else {
        found.add(new Found(Family.PERCENT, value));
      }
    }
    for (Pattern money : List.of(MONEY_BEFORE, MONEY_AFTER)) {
      Matcher mm = money.matcher(text);
      while (mm.find()) {
        found.add(new Found(Family.MONEY, Double.parseDouble(mm.group(1))));
      }
    }
    if (DOUBLE.matcher(text).find()) {
      found.add(new Found(Family.MULTIPLIER, 2));
    }
    return found;
  }

  /** The family of a fact's unit, or {@code null} for units no regex reads (counts, booleans). */
  static Family familyOf(Unit unit) {
    return switch (unit) {
      case DAYS, WEEKS -> Family.DAYS;
      case MONTHS -> Family.MONTHS;
      case HOURS_PER_DAY, HOURS_PER_WEEK -> Family.HOURS;
      case PERCENT -> Family.PERCENT;
      case MULTIPLIER -> Family.MULTIPLIER;
      case BDT_PER_MONTH -> Family.MONEY;
      case COUNT, BOOLEAN -> null;
    };
  }

  /**
   * {@code null} when no regex result applies (no unit family, no number of that family in the
   * clause); otherwise whether one found number equals the fact's value.
   */
  static Boolean agrees(ClauseFact fact, List<Found> found) {
    Family family = familyOf(fact.unit());
    if (family == null || fact.value() == null) {
      return null;
    }
    double canonical = fact.unit() == Unit.WEEKS ? fact.value() * 7 : fact.value();
    List<Found> sameFamily = found.stream().filter(f -> f.family() == family).toList();
    if (sameFamily.isEmpty()) {
      return null;
    }
    return sameFamily.stream().anyMatch(f -> Math.abs(f.value() - canonical) < 0.001);
  }
}
