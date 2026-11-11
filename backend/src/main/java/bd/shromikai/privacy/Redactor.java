package bd.shromikai.privacy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

  private static final int MIN_ENTITY_LENGTH = 2;

  private final PiiDetector detector;

  public Redactor(PiiDetector detector) {
    this.detector = detector;
  }

  /** Redacted paragraphs, in the order given. */
  public List<String> redact(List<String> paragraphs, LocalEntities entities, PlaceholderMap map) {
    List<List<Span>> spans = new ArrayList<>();
    for (String paragraph : paragraphs) {
      spans.add(spansOf(paragraph, entities));
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

  private List<Span> spansOf(String text, LocalEntities entities) {
    List<Span> all = new ArrayList<>();
    for (PiiMatch m : detector.detect(text)) {
      all.add(new Span(m.type(), m.start(), m.end(), true));
    }
    Map<PiiType, List<String>> byType =
        Map.of(
            PiiType.PERSON, entities.persons(),
            PiiType.ORG, entities.organisations(),
            PiiType.ADDRESS, entities.addresses(),
            PiiType.EMPLOYEE_ID, entities.employeeIds());
    byType.forEach(
        (type, values) -> {
          for (String value : values) {
            if (value == null || value.strip().length() < MIN_ENTITY_LENGTH) {
              continue;
            }
            String needle = value.strip();
            int from = 0;
            while ((from = text.indexOf(needle, from)) >= 0) {
              all.add(new Span(type, from, from + needle.length(), false));
              from += needle.length();
            }
          }
        });
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
}
