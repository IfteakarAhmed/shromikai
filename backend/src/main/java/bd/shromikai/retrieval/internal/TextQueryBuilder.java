package bd.shromikai.retrieval.internal;

import bd.shromikai.retrieval.TextTuning;
import bd.shromikai.shared.text.BanglaNormalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds the full-text side of a search (ADR-0013): the lexemes of the normalised query, minus the
 * words that occur in most sections (they do not discriminate), and the language a match must be
 * in. Lexical matching cannot cross languages, so an English query only searches English acts.
 */
@Component
public class TextQueryBuilder {

  /** Below this many sections an absolute floor keeps the filter off (tiny corpora, tests). */
  static final int MIN_CUTOFF = 20;

  /** Keep at least this many words, the rarest ones, when every word would be dropped. */
  static final int KEEP_AT_LEAST = 3;

  private static final Duration STATS_TTL = Duration.ofMinutes(10);

  /** The words to search and the language filter; {@code lexemes} is empty for no text search. */
  public record Query(List<String> lexemes, @Nullable String language) {}

  private final SectionSearchRepository repository;
  private final RetrievalProperties properties;
  private volatile SectionSearchRepository.DocFreq stats;
  private volatile long statsLoadedNanos;

  TextQueryBuilder(SectionSearchRepository repository, RetrievalProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public TextTuning defaultTuning() {
    return new TextTuning(properties.textMaxDocFreq(), properties.textLanguageFilter());
  }

  public Query build(String rawQuery, @Nullable TextTuning tuning) {
    TextTuning t = tuning == null ? defaultTuning() : tuning;
    String normalised = BanglaNormalizer.matchingForm(rawQuery);
    List<String> lexemes = repository.lexemes(normalised);
    if (t.maxDocFreq() < 1.0 && !lexemes.isEmpty()) {
      lexemes = filter(lexemes, stats(), t.maxDocFreq());
    }
    return new Query(lexemes, t.languageFilter() ? language(normalised) : null);
  }

  static List<String> filter(
      List<String> lexemes, SectionSearchRepository.DocFreq stats, double maxDocFreq) {
    long cutoff = Math.max(MIN_CUTOFF, (long) Math.ceil(maxDocFreq * stats.sections()));
    List<String> kept = new ArrayList<>();
    for (String lexeme : lexemes) {
      if (stats.counts().getOrDefault(lexeme, 0) <= cutoff) {
        kept.add(lexeme);
      }
    }
    if (kept.isEmpty()) {
      return lexemes.stream()
          .sorted(
              Comparator.comparingInt((String l) -> stats.counts().getOrDefault(l, 0))
                  .thenComparing(Comparator.naturalOrder()))
          .limit(KEEP_AT_LEAST)
          .toList();
    }
    return List.copyOf(kept);
  }

  /** {@code bn} if the text has a Bengali letter, else {@code en} if it has a Latin letter. */
  static @Nullable String language(String text) {
    boolean latin = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= '\u0980' && c <= '\u09FF') {
        return "bn";
      }
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        latin = true;
      }
    }
    return latin ? "en" : null;
  }

  private SectionSearchRepository.DocFreq stats() {
    SectionSearchRepository.DocFreq current = stats;
    if (current == null || System.nanoTime() - statsLoadedNanos > STATS_TTL.toNanos()) {
      current = repository.docFreq();
      stats = current;
      statsLoadedNanos = System.nanoTime();
    }
    return current;
  }
}
