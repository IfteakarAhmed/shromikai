package bd.shromikai.law.internal;

import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Terms defined in an act's definitions section and where they are used (FR-16 {@code
 * USES_DEFINITION}). Definitions are read from the text the source printed, for example {@code (১)
 * "অবসর" অর্থ ...}; nothing is added from outside the corpus (R-10).
 */
final class DefinitionExtractor {

  /** A defined term in display form and in matching form. */
  record Definition(String term, String matching, Pattern usage) {}

  private static final String QUOTES = "\"“‘'";
  private static final String CLOSE = "\"”’'";
  private static final Pattern BN_DEFINITION =
      Pattern.compile(
          "^\\s*\\[?\\s*\\(\\s*[0-9\u09E6-\u09EF]+[A-Za-z\u0995-\u09B9]*\\s*\\)\\s*"
              + "(?:[^"
              + QUOTES
              + "]{0,60}?[,\\s])?"
              + "["
              + QUOTES
              + "]+([^"
              + CLOSE
              + "]{1,80})["
              + CLOSE
              + "]+\\s*(?:অর্থ|বলিতে|বলিলে)");
  private static final Pattern EN_MEANS =
      Pattern.compile(
          "["
              + QUOTES
              + "]([A-Za-z][^"
              + CLOSE
              + "]{0,60})["
              + CLOSE
              + "]\\s*(?:means|includes|has the meaning)");
  private static final Pattern EN_CALLED =
      Pattern.compile(
          "called\\s+(?:the\\s+)?[" + QUOTES + "]([A-Za-z][^" + CLOSE + "]{0,60})[" + CLOSE + "]");

  /** Terms with fewer base letters (marks do not count) are too short to match reliably. */
  static final int MIN_TERM_LETTERS = 3;

  private DefinitionExtractor() {}

  private static long letters(String text) {
    return text.codePoints().filter(Character::isLetter).count();
  }

  /** Defined terms of a definitions section, in source order, without duplicates. */
  static List<Definition> definitions(String definitionsText) {
    Map<String, Definition> found = new LinkedHashMap<>();
    for (String line : definitionsText.split("\n")) {
      List<String> terms = new ArrayList<>();
      Matcher bn = BN_DEFINITION.matcher(line);
      if (bn.find()) {
        terms.add(bn.group(1));
      }
      Matcher means = EN_MEANS.matcher(line);
      while (means.find()) {
        terms.add(means.group(1));
      }
      Matcher called = EN_CALLED.matcher(line);
      while (called.find()) {
        terms.add(called.group(1));
      }
      for (String term : terms) {
        String clean = term.strip();
        String matching = BanglaNormalizer.matchingForm(clean);
        if (letters(matching) >= MIN_TERM_LETTERS && !found.containsKey(matching)) {
          found.put(
              matching,
              new Definition(
                  clean,
                  matching,
                  Pattern.compile("(?<![\\p{L}\\p{M}\\d])" + Pattern.quote(matching))));
        }
      }
    }
    return new ArrayList<>(found.values());
  }

  /** Definitions whose term appears (at a word start, any inflection) in {@code sectionText}. */
  static List<Definition> usedIn(List<Definition> definitions, String sectionText) {
    String matching = BanglaNormalizer.matchingForm(sectionText);
    return definitions.stream().filter(d -> d.usage().matcher(matching).find()).toList();
  }
}
