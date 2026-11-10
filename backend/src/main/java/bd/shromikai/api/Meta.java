package bd.shromikai.api;

import bd.shromikai.law.CorpusStats;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Response of {@code GET /api/v1/meta} (api.md §3, schema {@code Meta}). {@code audit} stays {@code
 * null} until P9.
 */
@Schema(name = "Meta")
public record Meta(
    String name, String version, Models models, Corpus corpus, @Nullable Object audit) {

  /**
   * Model ID per role; {@code verifier} and {@code judge} are null without {@code GEMINI_MODEL}.
   */
  @Schema(name = "MetaModels")
  public record Models(
      String reasoning,
      String fast,
      @Nullable String verifier,
      String verifierFallback,
      @Nullable String judge,
      String localPii,
      String embedding) {}

  /** Corpus counts; {@code release} and {@code lastRefreshAt} are null until P3. */
  @Schema(name = "MetaCorpus")
  public record Corpus(
      @Nullable String release,
      long acts,
      long sections,
      long currentSections,
      long embeddedSections,
      @Nullable Instant lastRefreshAt) {

    static Corpus from(CorpusStats stats) {
      return new Corpus(
          stats.release(),
          stats.acts(),
          stats.sections(),
          stats.currentSections(),
          stats.embeddedSections(),
          stats.lastRefreshAt());
    }
  }
}
