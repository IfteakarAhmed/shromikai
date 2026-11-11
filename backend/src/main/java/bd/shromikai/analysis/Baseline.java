package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.VerificationStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The plain-LLM answer of compare mode (FR-59): no retrieval, no verifier. Every citation it
 * proposed was then checked by the verifier and carries the status and reason codes; a citation
 * that is not {@code VERIFIED} is shown only as a claim marked with its status, never as a
 * citation.
 *
 * @param status {@code OK} or {@code UNAVAILABLE}
 * @param reason a content-free reason code when unavailable
 */
public record Baseline(
    String status, @Nullable String reason, @Nullable String modelId, List<Finding> findings) {

  public Baseline {
    findings = List.copyOf(findings);
  }

  public static Baseline unavailable(String reason) {
    return new Baseline("UNAVAILABLE", reason, null, List.of());
  }

  /** A baseline finding with its checked citations. */
  public record Finding(
      SensitiveText title, String severity, SensitiveText explanation, List<Checked> citations) {

    public Finding {
      citations = List.copyOf(citations);
    }
  }

  /** A claimed citation and what the verifier said about it. */
  public record Checked(
      ClaimedCitation claimed, VerificationStatus status, List<String> reasonCodes) {

    public Checked {
      reasonCodes = List.copyOf(reasonCodes);
    }
  }
}
