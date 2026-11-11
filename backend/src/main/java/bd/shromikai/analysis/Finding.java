package bd.shromikai.analysis;

import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.VerifiedCitation;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One risk of the analysis. Until the verifier has run, {@code claims} hold what a model or rule
 * proposed; only {@code citations}, all {@link VerifiedCitation}s, can reach a user (R-01). The
 * text fields paraphrase the redacted contract, so they are {@link SensitiveText} (R-02).
 *
 * @param clauseIds the clauses concerned; empty only for an omission
 * @param claims proposed citations awaiting or having failed verification
 * @param citations verified citations; non-empty exactly when {@code verification} is VERIFIED
 * @param reasonCodes why the verifier did not accept the claims (content-free codes)
 * @param reviewItemId the review item of an UNCERTAIN finding or a rule/LLM conflict
 * @param redlineId the redline proposed for a VERIFIED finding
 */
public record Finding(
    String id,
    SensitiveText title,
    FindingKind kind,
    Severity severity,
    boolean nonObvious,
    FindingSource source,
    String clauseType,
    List<String> clauseIds,
    SensitiveText explanation,
    SensitiveText whyItMatters,
    double confidence,
    @Nullable String ruleId,
    List<ClaimedCitation> claims,
    FindingVerification verification,
    List<VerifiedCitation> citations,
    List<String> reasonCodes,
    @Nullable UUID reviewItemId,
    @Nullable String redlineId) {

  public Finding {
    clauseIds = List.copyOf(clauseIds);
    claims = List.copyOf(claims);
    citations = List.copyOf(citations);
    reasonCodes = List.copyOf(reasonCodes);
  }

  public Finding withId(String newId) {
    return copy(
        newId,
        claims,
        verification,
        citations,
        reasonCodes,
        reviewItemId,
        redlineId,
        confidence,
        severity,
        explanation,
        source,
        ruleId);
  }

  public Finding withClaims(List<ClaimedCitation> newClaims) {
    return copy(
        id,
        newClaims,
        verification,
        citations,
        reasonCodes,
        reviewItemId,
        redlineId,
        confidence,
        severity,
        explanation,
        source,
        ruleId);
  }

  /** The verifier accepted at least one claim: only the verified citations remain (R-01). */
  public Finding verified(List<VerifiedCitation> accepted) {
    if (accepted.isEmpty()) {
      throw new IllegalArgumentException("A VERIFIED finding needs a verified citation");
    }
    return copy(
        id,
        List.of(),
        FindingVerification.VERIFIED,
        accepted,
        List.of(),
        reviewItemId,
        redlineId,
        confidence,
        severity,
        explanation,
        source,
        ruleId);
  }

  /** No claim was verified: no citation is shown, the claims stay for the corrective loop. */
  public Finding uncertain(List<String> reasons) {
    return copy(
        id,
        claims,
        FindingVerification.UNCERTAIN,
        List.of(),
        reasons,
        reviewItemId,
        redlineId,
        confidence,
        severity,
        explanation,
        source,
        ruleId);
  }

  public Finding withReviewItem(UUID item) {
    return copy(
        id,
        claims,
        verification,
        citations,
        reasonCodes,
        item,
        redlineId,
        confidence,
        severity,
        explanation,
        source,
        ruleId);
  }

  public Finding withRedline(String redline) {
    return copy(
        id,
        claims,
        verification,
        citations,
        reasonCodes,
        reviewItemId,
        redline,
        confidence,
        severity,
        explanation,
        source,
        ruleId);
  }

  /** The merge of a rule finding with an LLM finding on the same issue (FR-49). */
  public Finding merged(
      double newConfidence, SensitiveText newExplanation, List<ClaimedCitation> ruleClaims) {
    return copy(
        id,
        ruleClaims,
        verification,
        citations,
        reasonCodes,
        reviewItemId,
        redlineId,
        newConfidence,
        severity,
        newExplanation,
        FindingSource.RULE_ENGINE,
        ruleId);
  }

  private Finding copy(
      String newId,
      List<ClaimedCitation> newClaims,
      FindingVerification newVerification,
      List<VerifiedCitation> newCitations,
      List<String> newReasons,
      @Nullable UUID newReview,
      @Nullable String newRedline,
      double newConfidence,
      Severity newSeverity,
      SensitiveText newExplanation,
      FindingSource newSource,
      @Nullable String newRule) {
    return new Finding(
        newId,
        title,
        kind,
        newSeverity,
        nonObvious,
        newSource,
        clauseType,
        clauseIds,
        newExplanation,
        whyItMatters,
        newConfidence,
        newRule,
        newClaims,
        newVerification,
        newCitations,
        newReasons,
        newReview,
        newRedline);
  }
}
