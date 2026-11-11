package bd.shromikai.verify;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The verification decision table (architecture §7.4, FR-45, ADR-0002) as a pure function. Checks
 * short-circuit in the order existence, quote, support, so only the combinations of the table can
 * occur; any other combination is a programming error and throws.
 *
 * <pre>
 * existence quote   support                               status
 * FAIL      SKIPPED SKIPPED                               REJECTED
 * PASS      FAIL    SKIPPED                               REJECTED
 * PASS      PASS    SUPPORTS, confidence &gt;= 0.70          VERIFIED
 * PASS      PASS    RULE_PROVENANCE or HUMAN              VERIFIED
 * PASS      PASS    PARTIAL, or SUPPORTS below 0.70       UNCERTAIN
 * PASS      PASS    verifier unavailable after fallback   UNCERTAIN
 * PASS      PASS    CONTRADICTS or UNRELATED              REJECTED
 * </pre>
 */
public final class DecisionTable {

  private DecisionTable() {}

  /** The support input of the table. */
  public sealed interface SupportInput {

    /** Check (c) did not run because (a) or (b) failed. */
    record Skipped() implements SupportInput {}

    /** An independent model judged the claim; malformed output is passed as UNRELATED. */
    record Judged(SupportLabel label, double confidence) implements SupportInput {}

    /** A reviewed rule or mapping row ({@code RULE_PROVENANCE}) or a human reviewer. */
    record Provenance(boolean human) implements SupportInput {}

    /** No verifier could be reached, even after the fallback. */
    record Unavailable() implements SupportInput {}
  }

  /**
   * The decided status and the reason codes of the support check ({@code a} and {@code b} add
   * theirs).
   */
  public record Decision(VerificationStatus status, List<ReasonCode> supportReasons) {}

  public static Decision decide(
      CheckOutcome existence, CheckOutcome quote, SupportInput support, double minConfidence) {
    if (existence == CheckOutcome.FAIL) {
      require(
          quote == CheckOutcome.SKIPPED && support instanceof SupportInput.Skipped,
          existence,
          quote,
          support);
      return new Decision(VerificationStatus.REJECTED, List.of());
    }
    if (existence != CheckOutcome.PASS) {
      throw invalid(existence, quote, support);
    }
    if (quote == CheckOutcome.FAIL) {
      require(support instanceof SupportInput.Skipped, existence, quote, support);
      return new Decision(VerificationStatus.REJECTED, List.of());
    }
    if (quote != CheckOutcome.PASS) {
      throw invalid(existence, quote, support);
    }
    return switch (support) {
      case SupportInput.Skipped skipped -> throw invalid(existence, quote, support);
      case SupportInput.Provenance provenance ->
          new Decision(VerificationStatus.VERIFIED, List.of());
      case SupportInput.Unavailable unavailable ->
          new Decision(VerificationStatus.UNCERTAIN, List.of(ReasonCode.VERIFIER_UNAVAILABLE));
      case SupportInput.Judged judged -> judged(judged, minConfidence);
    };
  }

  private static Decision judged(SupportInput.Judged judged, double minConfidence) {
    return switch (judged.label()) {
      case SUPPORTS ->
          judged.confidence() >= minConfidence
              ? new Decision(VerificationStatus.VERIFIED, List.of())
              : new Decision(
                  VerificationStatus.UNCERTAIN, List.of(ReasonCode.SUPPORT_LOW_CONFIDENCE));
      case PARTIAL ->
          new Decision(VerificationStatus.UNCERTAIN, List.of(ReasonCode.SUPPORT_PARTIAL));
      case CONTRADICTS ->
          new Decision(VerificationStatus.REJECTED, List.of(ReasonCode.SUPPORT_CONTRADICTS));
      case UNRELATED ->
          new Decision(VerificationStatus.REJECTED, List.of(ReasonCode.SUPPORT_UNRELATED));
    };
  }

  private static void require(
      boolean condition,
      CheckOutcome existence,
      CheckOutcome quote,
      @Nullable SupportInput support) {
    if (!condition) {
      throw invalid(existence, quote, support);
    }
  }

  private static IllegalArgumentException invalid(
      CheckOutcome existence, CheckOutcome quote, @Nullable SupportInput support) {
    return new IllegalArgumentException(
        "Not a combination of the decision table: existence="
            + existence
            + ", quote="
            + quote
            + ", support="
            + support);
  }
}
