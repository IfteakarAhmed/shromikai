package bd.shromikai.verify;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.text.OverlapChecker;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds review items that hold no contract text (FR-50, R-02). The paraphrase and the claim must
 * share no 8-token sequence with the clauses of the document ({@link OverlapChecker}). A paraphrase
 * that does is regenerated once and, if it still does, replaced by the facts-only form; a claim
 * that does is replaced at once and the item says so with {@code claimReduced}; a claimed quote
 * that does is dropped.
 */
@Component
public final class ReviewItemFactory {

  /** Review items expire with the job's retention (architecture §14). */
  static final Duration RETENTION = Duration.ofDays(30);

  static final int MAX_SUMMARY = 400;
  static final int MAX_CLAIM = 600;
  static final int MAX_QUOTE = 300;

  /**
   * What the factory needs.
   *
   * @param paraphrase the first redacted paraphrase, for example the finding's explanation
   * @param claimedSectionKey the section the failed citation named, or {@code null}
   * @param sourceClauses redacted text of every clause of the document, used only for the overlap
   *     check (an omission finding has no clauses of its own, a model can copy from any)
   */
  public record Draft(
      UUID analysisId,
      String findingId,
      String clauseType,
      String findingKind,
      String severity,
      String side,
      List<FactView> facts,
      String paraphrase,
      @Nullable String claimedSectionKey,
      @Nullable String claimedQuote,
      @Nullable SensitiveText claim,
      List<String> reasonCodes,
      List<String> candidateSectionKeys,
      List<SensitiveText> sourceClauses) {}

  private final Clock clock;

  public ReviewItemFactory(Clock clock) {
    this.clock = clock;
  }

  /**
   * @param regenerate asked once for a new paraphrase when the first one is unusable
   */
  public ReviewItem create(Draft draft, Supplier<Optional<String>> regenerate) {
    OverlapChecker overlap =
        new OverlapChecker(
            draft.sourceClauses().stream().map(SensitiveText::reveal).collect(Collectors.toList()));
    String factsOnly = factsOnly(draft);
    boolean reduced = false;

    String summary = usable(draft.paraphrase(), MAX_SUMMARY, overlap);
    if (summary == null) {
      summary = usable(regenerate.get().orElse(""), MAX_SUMMARY, overlap);
    }
    if (summary == null) {
      summary = factsOnly;
      reduced = true;
    }

    String claim = null;
    if (draft.claim() != null) {
      claim = usable(draft.claim().reveal(), MAX_CLAIM, overlap);
      if (claim == null) {
        claim = factsOnly;
        reduced = true;
      }
    }
    String quote = draft.claimedQuote();
    if (quote != null && overlap.overlaps(quote)) {
      quote = null; // a quote copied from the document is contract text (R-02)
    }
    if (quote != null && quote.length() > MAX_QUOTE) {
      quote = quote.substring(0, MAX_QUOTE);
    }
    var now = clock.instant();
    return new ReviewItem(
        UUID.randomUUID(),
        draft.analysisId(),
        draft.findingId(),
        draft.clauseType(),
        draft.findingKind(),
        draft.severity(),
        draft.side(),
        summary,
        draft.facts(),
        draft.claimedSectionKey(),
        quote,
        claim,
        reduced,
        draft.reasonCodes(),
        draft.candidateSectionKeys(),
        now,
        now.plus(RETENTION));
  }

  /** The text if it is non-blank, short enough and shares no 8-token window with the source. */
  private static @Nullable String usable(String text, int maxChars, OverlapChecker overlap) {
    String trimmed = text == null ? "" : text.strip();
    if (trimmed.isEmpty() || trimmed.length() > maxChars || overlap.overlaps(trimmed)) {
      return null;
    }
    return trimmed;
  }

  /** Clause type, kind, severity and typed facts: derived data, never contract text. */
  static String factsOnly(Draft draft) {
    String facts =
        draft.facts().isEmpty()
            ? "no typed facts"
            : draft.facts().stream().map(FactView::display).collect(Collectors.joining("; "));
    String text =
        "Clause type "
            + draft.clauseType()
            + ", finding kind "
            + draft.findingKind()
            + ", severity "
            + draft.severity()
            + ". Facts: "
            + facts
            + ".";
    return text.length() <= MAX_SUMMARY ? text : text.substring(0, MAX_SUMMARY);
  }
}
