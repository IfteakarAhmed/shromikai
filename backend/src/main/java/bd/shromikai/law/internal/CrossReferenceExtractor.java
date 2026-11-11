package bd.shromikai.law.internal;

import bd.shromikai.law.SectionNumbers;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Finds references to other sections of the same act in statute text for the {@code REFERS_TO}
 * edges (FR-16): "ধারা ২৬(১)", "section 27(3A)", "sub-section (2) of section 100", "sections 24 to
 * 26", "২৮ ধারা". References to other acts ("section 5 of the Sale of Goods Act", "ধারা ১৭৫ ...
 * আইন, ২০২৬") are skipped, and dates or act numbers never match because a reference needs the word
 * "section" or "ধারা".
 */
final class CrossReferenceExtractor {

  /** A reference to a section (and sub-section, when the text names one). */
  record Reference(String sectionNo, @Nullable String subsection) {
    String label() {
      return subsection == null ? sectionNo : sectionNo + "(" + subsection + ")";
    }
  }

  private static final String LETTERS = "A-Za-zক-হ";
  private static final String NUM = "[0-9০-৯]+[" + LETTERS + "]{0,2}";
  private static final String SUB = "\\(\\s*(" + NUM + ")\\s*\\)";

  private static final Pattern BN_WORD =
      Pattern.compile("(?<![\\p{L}\\p{M}-])ধারা(?:সমূহ)?(?![\\p{L}\\p{M}])");
  private static final Pattern EN_WORD =
      Pattern.compile("(?<![\\p{L}-])sections?(?![\\p{L}])", Pattern.CASE_INSENSITIVE);
  private static final Pattern BN_POSTFIX =
      Pattern.compile("(?<![\\p{L}\\p{M}\\d০-৯.(-])(" + NUM + ")\\s*ধারা(?![\\p{L}\\p{M}])");
  private static final Pattern ITEM = Pattern.compile("\\s*(" + NUM + ")(?:\\s*" + SUB + ")?");
  private static final Pattern QUALIFIER =
      Pattern.compile(
          "\\s*(?:এর|এ)\\s*(?:(উপ-?ধারা)|উপ-?দফা|দফা)\\s*" + SUB + "(?:\\s*" + SUB + ")*");
  private static final Pattern SEPARATOR =
      Pattern.compile(
          "(?:\\s*,\\s*|\\s+(?:(ও|এবং|বা|অথবা|and|or)|(হইতে|to|through))\\s+)"
              + "(?:(?:ধারা|sections?)\\s*)?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SUBSECTION_BEFORE =
      Pattern.compile(
          "sub-?sections?\\s*\\(\\s*(" + NUM + ")\\s*\\)\\s*of\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BN_OTHER_ACT =
      Pattern.compile(
          "^\\s*(?:এর|এ|অধীন|এর অধীন)?\\s*(.{0,60}?)(?:আইন|অধ্যাদেশ|বিধিমালা)\\s*,\\s*[0-9০-৯]{4}");
  private static final Pattern EN_OTHER_ACT =
      Pattern.compile(
          "^\\s+of\\s+(?:the\\s+)?((?:[A-Z][\\w'’-]*\\s+(?:of\\s+)?)+)(?:Act|Code|Ordinance|Order)\\b");

  private static final int MAX_RANGE = 50;

  private final List<String> ownNames;

  /**
   * @param ownNames names of this act (matching form irrelevant), so "of the Contract Act" stays.
   */
  CrossReferenceExtractor(Set<String> ownNames) {
    this.ownNames = ownNames.stream().map(BanglaNormalizer::matchingForm).toList();
  }

  List<Reference> extract(String text) {
    Map<String, Reference> found = new LinkedHashMap<>();
    scan(text, BN_WORD, found);
    scan(text, EN_WORD, found);
    Matcher postfix = BN_POSTFIX.matcher(text);
    while (postfix.find()) {
      add(found, postfix.group(1), null);
    }
    return new ArrayList<>(found.values());
  }

  private void scan(String text, Pattern trigger, Map<String, Reference> found) {
    Matcher word = trigger.matcher(text);
    while (word.find()) {
      String beforeSub = null;
      Matcher before = SUBSECTION_BEFORE.matcher(text.substring(0, word.start()));
      if (before.find()) {
        beforeSub = latinSub(before.group(1));
      }
      List<Reference> refs = new ArrayList<>();
      int pos = parseList(text, word.end(), beforeSub, refs);
      if (!refs.isEmpty() && !refersToOtherAct(text, pos)) {
        refs.forEach(r -> found.putIfAbsent(r.label(), r));
      }
    }
  }

  /** Reads {@code ITEM (qualifier) (separator ITEM ...)*} from {@code start}; returns the end. */
  private int parseList(String text, int start, @Nullable String firstSub, List<Reference> out) {
    int pos = start;
    Reference previous = null;
    boolean range = false;
    String pendingSub = firstSub;
    while (true) {
      Matcher item = ITEM.matcher(text);
      item.region(pos, text.length());
      if (!item.lookingAt() || isYear(item.group(1))) {
        break;
      }
      String sectionNo = SectionNumbers.toLatin(item.group(1)).orElse(null);
      if (sectionNo == null) {
        break;
      }
      String sub = item.group(2) != null ? latinSub(item.group(2)) : pendingSub;
      pendingSub = null;
      pos = item.end();
      Matcher qualifier = QUALIFIER.matcher(text);
      qualifier.region(pos, text.length());
      if (qualifier.lookingAt()) {
        if (qualifier.group(1) != null) {
          sub = latinSub(qualifier.group(2));
        }
        pos = qualifier.end();
      }
      Reference current = new Reference(sectionNo, sub);
      if (range && previous != null) {
        expand(previous, current, out);
      } else {
        out.add(current);
      }
      previous = current;
      Matcher separator = SEPARATOR.matcher(text);
      separator.region(pos, text.length());
      if (!separator.lookingAt()) {
        break;
      }
      range = separator.group(2) != null;
      pos = separator.end();
    }
    return pos;
  }

  /** Adds the sections between two plain numbers ({@code 24 to 26}); letters are not expanded. */
  private static void expand(Reference from, Reference to, List<Reference> out) {
    out.removeIf(r -> r.equals(to));
    if (from.sectionNo().matches("[0-9]+") && to.sectionNo().matches("[0-9]+")) {
      int low = Integer.parseInt(from.sectionNo());
      int high = Integer.parseInt(to.sectionNo());
      if (low < high && high - low <= MAX_RANGE) {
        for (int n = low + 1; n < high; n++) {
          out.add(new Reference(String.valueOf(n), null));
        }
      }
    }
    out.add(to);
  }

  private boolean refersToOtherAct(String text, int pos) {
    int end = pos;
    int limit = Math.min(text.length(), pos + 100);
    while (end < limit) {
      char c = text.charAt(end);
      if (c == '।' || c == '৷' || c == ';' || c == '\n') {
        break;
      }
      end++;
    }
    String window = text.substring(pos, end);
    Matcher bn = BN_OTHER_ACT.matcher(window);
    if (bn.find()) {
      return !namesOwnAct(bn.group(1));
    }
    Matcher en = EN_OTHER_ACT.matcher(window);
    return en.find() && !namesOwnAct(en.group(1));
  }

  private boolean namesOwnAct(String name) {
    String matching = BanglaNormalizer.matchingForm(name);
    return matching.startsWith("এই")
        || ownNames.stream().anyMatch(own -> own.contains(matching) || matching.contains(own));
  }

  private void add(Map<String, Reference> found, String printed, @Nullable String sub) {
    SectionNumbers.toLatin(printed)
        .filter(n -> !isYear(printed))
        .ifPresent(n -> found.putIfAbsent(new Reference(n, sub).label(), new Reference(n, sub)));
  }

  private static boolean isYear(String printed) {
    String latin = BanglaNormalizer.toLatinDigits(printed);
    return latin.matches("(1[89]|20)[0-9]{2}");
  }

  private static String latinSub(String printed) {
    return SectionNumbers.toLatin(printed).orElse(BanglaNormalizer.toLatinDigits(printed));
  }
}
