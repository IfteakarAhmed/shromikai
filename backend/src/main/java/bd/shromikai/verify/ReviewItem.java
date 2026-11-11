package bd.shromikai.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A finding that no verified citation backs, queued for a human (api.md §5.13, FR-50). It contains
 * no contract text: the summary and claim share no 8-token sequence with the source document, or
 * are replaced by the facts-only form.
 *
 * @param riskSummary redacted paraphrase of at most 400 characters
 * @param claimedQuote statute text the model quoted, at most 300 characters (not contract text)
 * @param claimedClaim redacted claim of at most 600 characters, or the facts-only form
 * @param claimReduced true when the claim or summary was replaced by the facts-only form
 */
public record ReviewItem(
    UUID id,
    UUID analysisId,
    String findingId,
    String clauseType,
    String findingKind,
    String severity,
    String side,
    String riskSummary,
    List<FactView> facts,
    @Nullable String claimedSectionKey,
    @Nullable String claimedQuote,
    @Nullable String claimedClaim,
    boolean claimReduced,
    List<String> reasonCodes,
    List<String> candidateSectionKeys,
    Instant createdAt,
    Instant expiresAt) {

  public ReviewItem {
    facts = List.copyOf(facts);
    reasonCodes = List.copyOf(reasonCodes);
    candidateSectionKeys = List.copyOf(candidateSectionKeys);
  }
}
