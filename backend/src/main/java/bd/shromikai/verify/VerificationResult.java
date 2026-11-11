package bd.shromikai.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The outcome of verifying one citation (api.md §5.6). Contains no contract text. */
public record VerificationResult(
    UUID citationId,
    String sectionKey,
    VerificationStatus status,
    int attempt,
    Checks checks,
    List<ReasonCode> reasonCodes,
    Instant verifiedAt,
    String corpusRelease) {

  public VerificationResult {
    reasonCodes = List.copyOf(reasonCodes);
  }

  public record Checks(Existence existence, QuoteMatch quoteMatch, Support support) {}

  public record Existence(CheckOutcome outcome) {}

  public record QuoteMatch(
      CheckOutcome outcome, @Nullable Double similarity, @Nullable String matchedLanguage) {}

  public record Support(
      CheckOutcome outcome,
      @Nullable SupportLabel label,
      @Nullable Double confidence,
      @Nullable VerifierIndependence independence,
      @Nullable String modelId,
      @Nullable String reason) {

    public static Support skipped() {
      return new Support(CheckOutcome.SKIPPED, null, null, null, null, null);
    }
  }
}
