package bd.shromikai.law.internal;

import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the amendment footnotes of a section (substitution, insertion, omission) and derives the
 * version label of the section (FR-13, ADR-0011 decision 4). Amendments are only ever detected from
 * the source: no act is assumed to exist.
 */
final class AmendmentParser {

  static final String ORIGINAL = "original";

  private static final Pattern BN_CITATION =
      Pattern.compile("\\(\\s*(\\d{4})\\s*সনের\\s*(\\d+)\\s*নং\\s*(?:আইন|অধ্যাদেশ)\\s*\\)");
  private static final Pattern EN_CITATION =
      Pattern.compile(
          "\\(\\s*(?:Act|Ordinance|President's Order|P\\.?O\\.?|Order|Regulation)\\s*"
              + "(?:No\\.?)?\\s*([IVXLCDM0-9]+)\\s+of\\s+(\\d{4})\\s*\\)");
  private static final Pattern OMISSION_ONLY =
      Pattern.compile(
          "^(?:[0-9০-৯][^\\s]*\\s*[.।৷]\\s*)?\\[?\\s*"
              + "(?:\\*\\*\\*|বিলুপ্ত|রহিত|repealed|omitted)\\s*\\]?\\s*\\.?$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern OMISSION_WORD =
      Pattern.compile(
          "^\\[?\\s*(?:repealed|omitted|বিলুপ্ত|রহিত)(?![\\p{L}\\p{M}])", Pattern.CASE_INSENSITIVE);

  private AmendmentParser() {}

  /**
   * A footnote. The amending act is the first act citation in the text, for example "(২০২৬ সনের ৪৩
   * নং আইন)" or "(Act No. VI of 1899)"; its key is {@code year-number} (ADR-0011).
   */
  static AmendmentNote note(String text) {
    String collapsed = text.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
    String latin = BanglaNormalizer.toLatinDigits(collapsed);
    Matcher bn = BN_CITATION.matcher(latin);
    Matcher en = EN_CITATION.matcher(latin);
    String key = null;
    String citation = null;
    int at = Integer.MAX_VALUE;
    if (bn.find()) {
      key = bn.group(1) + "-" + bn.group(2);
      citation = collapsed.substring(bn.start(), bn.end());
      at = bn.start();
    }
    if (en.find() && en.start() < at) {
      key = en.group(2) + "-" + en.group(1).toLowerCase(java.util.Locale.ROOT);
      citation = collapsed.substring(en.start(), en.end());
    }
    return new AmendmentNote(kind(collapsed), collapsed, key, citation);
  }

  static AmendmentKind kind(String text) {
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("প্রতিস্থাপিত") || lower.contains("substituted")) {
      return AmendmentKind.SUBSTITUTED;
    }
    if (lower.contains("সন্নিবেশিত")
        || lower.contains("সংযোজিত")
        || lower.contains("inserted")
        || lower.contains("added")) {
      return AmendmentKind.INSERTED;
    }
    if (lower.contains("বিলুপ্ত")
        || lower.contains("রহিত")
        || lower.contains("omitted")
        || lower.contains("repealed")) {
      return AmendmentKind.OMITTED;
    }
    if (lower.contains("সংশোধিত") || lower.contains("amended")) {
      return AmendmentKind.AMENDED;
    }
    return AmendmentKind.OTHER;
  }

  /** Year of an amending act key such as {@code 2026-43}. */
  static int yearOf(String key) {
    return Integer.parseInt(key.substring(0, 4));
  }

  /**
   * Version label per amending act key: {@code amend-{year}}, or {@code amend-{year}-{number}} when
   * two detected acts share a year.
   */
  static Map<String, String> labels(Map<String, String> amendingActs) {
    Map<Integer, Integer> perYear = new TreeMap<>();
    amendingActs.keySet().forEach(key -> perYear.merge(yearOf(key), 1, Integer::sum));
    Map<String, String> labels = new LinkedHashMap<>();
    amendingActs
        .keySet()
        .forEach(
            key ->
                labels.put(
                    key, perYear.get(yearOf(key)) > 1 ? "amend-" + key : "amend-" + yearOf(key)));
    return labels;
  }

  /** Label of the latest amending act among {@code notes}, {@link #ORIGINAL} when there is none. */
  static String latestLabel(List<AmendmentNote> notes, Map<String, String> labels) {
    return notes.stream()
        .map(AmendmentNote::amendingActId)
        .filter(id -> id != null && labels.containsKey(id))
        .max(
            Comparator.comparingInt(AmendmentParser::yearOf)
                .thenComparing(Comparator.naturalOrder()))
        .map(labels::get)
        .orElse(ORIGINAL);
  }

  /** True when a text starts with a repeal or omission word, such as "[Repealed by ...]". */
  static boolean startsWithOmissionWord(String text) {
    return OMISSION_WORD.matcher(text.strip()).find();
  }

  /** True when a section's text is only the source's omission marker, such as {@code [***]}. */
  static boolean isOmissionMarker(String text) {
    return OMISSION_ONLY.matcher(text.replace(' ', ' ').strip()).matches();
  }
}
