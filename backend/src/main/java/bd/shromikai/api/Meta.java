package bd.shromikai.api;

import bd.shromikai.law.CorpusStats;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Response of {@code GET /api/v1/meta} (api.md §3, schema {@code Meta}). {@code recordedDemo} is
 * set only when the models are recorded answers (profile {@code fake-models}); the UI then says so
 * and offers only what was recorded (R-12).
 */
@Schema(name = "Meta")
public record Meta(
    String name,
    String version,
    Models models,
    Corpus corpus,
    @Nullable Audit audit,
    @Nullable RecordedDemo recordedDemo) {

  /** The one request the recorded answers cover: the sample contract, this side and language. */
  @Schema(name = "MetaRecordedDemo")
  public record RecordedDemo(String side, String outputLanguage) {}

  /** Audit counters for the live docs hub; {@code latestEval} is null until P10. */
  @Schema(name = "MetaAudit")
  public record Audit(
      long privacyBlocksLast7Days, long openReviewItems, @Nullable LatestEval latestEval) {}

  /** Headline numbers of the latest generated eval report (R-07, evaluation.md). */
  @Schema(name = "MetaLatestEval")
  public record LatestEval(
      String reportDate,
      long cases,
      double citationPrecision,
      long fabricatedCitationsShown,
      double riskRecall) {}

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
