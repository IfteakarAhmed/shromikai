package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.VerifiedCitation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A proposed revision of a clause for a {@code VERIFIED} finding (api.md §5.9). The word-level diff
 * is added with the memo in P7. The proposal passed the rule check, or there is no redline.
 *
 * @param clauseId {@code null} for an inserted clause
 * @param action {@code REPLACE} or {@code INSERT}
 * @param ruleIds the active rules the proposal was checked against
 * @param attempts 1, or 2 when the first proposal was regenerated
 */
public record Redline(
    String id,
    String findingId,
    @Nullable String clauseId,
    String action,
    @Nullable SensitiveText originalText,
    SensitiveText proposedText,
    SensitiveText rationale,
    List<VerifiedCitation> citations,
    List<String> ruleIds,
    int attempts,
    String language) {

  public Redline {
    citations = List.copyOf(citations);
    ruleIds = List.copyOf(ruleIds);
  }
}
