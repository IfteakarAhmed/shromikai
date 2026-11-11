package bd.shromikai.memo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the memo is assembled from (FR-51). The memo module does not depend on the analysis module,
 * so the caller maps its result into these records. Texts are already re-identified for the
 * requesting subject; the composer neither stores nor logs them (R-02).
 *
 * @param summary the model-written summary (or the fixed fallback)
 * @param nextSteps the model-written next steps (or the fixed fallback)
 */
public record MemoInput(
    UUID id,
    UUID analysisId,
    String language,
    String readingLevel,
    Instant generatedAt,
    String summary,
    List<String> nextSteps,
    List<FindingView> findings,
    List<String> redlineIds,
    List<String> obligationIds,
    Verification verification,
    Footer footer) {

  public MemoInput {
    nextSteps = List.copyOf(nextSteps);
    findings = List.copyOf(findings);
    redlineIds = List.copyOf(redlineIds);
    obligationIds = List.copyOf(obligationIds);
  }

  /**
   * One finding as the memo needs it.
   *
   * @param source RULE_ENGINE, LLM_ANALYSIS or PLAYBOOK
   * @param verificationStatus VERIFIED, UNCERTAIN or POLICY_ONLY
   * @param citationIds ids of the finding's {@code VerifiedCitation}s; empty unless VERIFIED
   */
  public record FindingView(
      String id,
      String title,
      String severity,
      String source,
      String verificationStatus,
      String explanation,
      String whyItMatters,
      List<String> citationIds,
      String redlineId) {

    public FindingView {
      citationIds = List.copyOf(citationIds);
    }
  }

  public record Verification(
      int verifiedCitations, int uncertainFindings, int policyFindings, String independence) {}

  public record Footer(String corpusRelease, String pipelineVersion, String keyId) {}
}
