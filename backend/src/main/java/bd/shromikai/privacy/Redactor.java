package bd.shromikai.privacy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces PII with typed placeholders (FR-29, architecture §10.3 and §10.4). Detector spans and
 * every exact occurrence of the strings the local model returned are collected for the whole
 * document first, overlaps are resolved (the longer span wins, a detector wins a tie), and then
 * numbers are assigned per type in order of first occurrence, so the same value always gets the
 * same placeholder. Money, durations and employment dates are kept (ADR-0003, ADR-0014).
 */
@Component
public final class Redactor {

  /** Shortest entity string or name part that is matched; shorter ones are too ambiguous. */
  static final int MIN_ENTITY_LENGTH = 3;

  private final PiiDetector detector;

  public Redactor(PiiDetector detector) {
    this.detector = detector;
  }

  /** Redacted paragraphs, in the order given. */
  public List<String> redact(List<String> paragraphs, LocalEntities entities, PlaceholderMap map) {
    List<Needle> needles = needles(entities);
    List<List<Span>> spans = new ArrayList<>();
    for (String paragraph : paragraphs) {
      spans.add(spansOf(paragraph, needles));
    }
    List<String> out = new ArrayList<>(paragraphs.size());
    for (int p = 0; p < paragraphs.size(); p++) {
      String text = paragraphs.get(p);
      StringBuilder sb = new StringBuilder(text.length());
      int cursor = 0;
      for (Span span : spans.get(p)) {
        sb.append(text, cursor, span.start);
        sb.append(map.placeholderFor(span.type, text.substring(span.start, span.end)));
        cursor = span.end;
      }
      sb.append(text, cursor, text.length());
      out.add(sb.toString());
    }
    return out;
  }

  /** Redacts one text (a single paragraph, claim or query). */
  public String redact(String text, LocalEntities entities, PlaceholderMap map) {
    return redact(List.of(text), entities, map).getFirst();
  }

  private record Needle(PiiType type, Pattern pattern) {}

  /**
   * Every entity string plus, for persons, each name part on its own (a surname alone is still
   * PII). Matching is case-insensitive, on NFC text, and only at word boundaries, so "Ali" does not
   * match inside "Alibaba".
   */
  private static List<Needle> needles(LocalEntities entities) {
    Map<PiiType, List<String>> byType = new LinkedHashMap<>();
    byType.put(PiiType.PERSON, entities.persons());
    byType.put(PiiType.ORG, entities.organisations());
    byType.put(PiiType.ADDRESS, entities.addresses());
    byType.put(PiiType.EMPLOYEE_ID, entities.employeeIds());
    Set<String> seen = new LinkedHashSet<>();
    List<Needle> out = new ArrayList<>();
    byType.forEach(
        (type, values) -> {
          for (String value : values) {
            if (value == null) {
              continue;
            }
            List<String> candidates = new ArrayList<>();
            candidates.add(value);
            if (type == PiiType.PERSON) {
              for (String part : value.strip().split("\\s+")) {
                candidates.add(part.replaceAll("^[\\p{P}]+|[\\p{P}]+$", ""));
              }
            }
            for (String candidate : candidates) {
              String needle = Normalizer.normalize(candidate.strip(), Normalizer.Form.NFC);
              if (needle.codePointCount(0, needle.length()) < MIN_ENTITY_LENGTH
                  || !seen.add(type + "|" + needle.toLowerCase(Locale.ROOT))) {
                continue;
              }
              out.add(
                  new Needle(
                      type,
                      Pattern.compile(
                          "(?<![\\p{L}\\p{M}\\p{N}])"
                              + Pattern.quote(needle)
                              + "(?![\\p{L}\\p{M}\\p{N}])",
                          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
            }
          }
        });
    return out;
  }

  private List<Span> spansOf(String text, List<Needle> needles) {
    List<Span> all = new ArrayList<>();
    for (PiiMatch m : detector.detect(text)) {
      all.add(new Span(m.type(), m.start(), m.end(), true));
    }
    NormalizedText normalized = NormalizedText.of(text);
    for (Needle needle : needles) {
      Matcher matcher = needle.pattern.matcher(normalized.text);
      while (matcher.find()) {
        all.add(
            new Span(
                needle.type,
                normalized.originalStart(matcher.start()),
                normalized.originalEnd(matcher.end() - 1),
                false));
      }
    }
    all.sort(
        Comparator.comparingInt((Span s) -> s.start)
            .thenComparing(Comparator.comparingInt((Span s) -> s.end - s.start).reversed())
            .thenComparing(s -> !s.rule));
    List<Span> kept = new ArrayList<>();
    int end = -1;
    for (Span span : all) {
      if (span.start >= end) {
        kept.add(span);
        end = span.end;
      }
    }
    return kept;
  }

  private record Span(PiiType type, int start, int end, boolean rule) {}

  /**
   * The NFC form of a text with, for every normalized char, the original range of the cluster (a
   * base character and its combining marks) it came from, so matches map back to the original.
   */
  private static final class NormalizedText {
    final String text;
    private final int[] starts;
    private final int[] ends;

    private NormalizedText(String text, int[] starts, int[] ends) {
      this.text = text;
      this.starts = starts;
      this.ends = ends;
    }

    static NormalizedText of(String original) {
      if (Normalizer.isNormalized(original, Normalizer.Form.NFC)) {
        int[] identity = new int[original.length() + 1];
        for (int i = 0; i < identity.length; i++) {
          identity[i] = i;
        }
        int[] ends = new int[original.length()];
        for (int i = 0; i < ends.length; i++) {
          ends[i] = i + 1;
        }
        return new NormalizedText(original, identity, ends);
      }
      StringBuilder out = new StringBuilder(original.length());
      int[] starts = new int[original.length() + 1];
      int[] ends = new int[original.length() + 1];
      int i = 0;
      while (i < original.length()) {
        int j = i + Character.charCount(original.codePointAt(i));
        while (j < original.length() && isMark(original.codePointAt(j))) {
          j += Character.charCount(original.codePointAt(j));
        }
        String cluster = Normalizer.normalize(original.substring(i, j), Normalizer.Form.NFC);
        for (int k = 0; k < cluster.length(); k++) {
          starts[out.length() + k] = i;
          ends[out.length() + k] = j;
        }
        out.append(cluster);
        i = j;
      }
      return new NormalizedText(out.toString(), starts, ends);
    }

    int originalStart(int normalizedIndex) {
      return starts[normalizedIndex];
    }

    int originalEnd(int lastNormalizedIndex) {
      return ends[lastNormalizedIndex];
    }

    private static boolean isMark(int codePoint) {
      int type = Character.getType(codePoint);
      return type == Character.NON_SPACING_MARK
          || type == Character.COMBINING_SPACING_MARK
          || type == Character.ENCLOSING_MARK;
    }
  }
}
