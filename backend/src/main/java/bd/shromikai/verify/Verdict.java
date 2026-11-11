package bd.shromikai.verify;

import org.jspecify.annotations.Nullable;

/**
 * What the verifier decided about one claimed citation: the result (always present, for the trace
 * and review items) and the {@link VerifiedCitation} (present only when the status is {@code
 * VERIFIED}).
 */
public record Verdict(VerificationResult result, @Nullable VerifiedCitation citation) {

  public boolean verified() {
    return result.status() == VerificationStatus.VERIFIED;
  }
}
