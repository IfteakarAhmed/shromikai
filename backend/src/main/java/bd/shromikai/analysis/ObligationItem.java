package bd.shromikai.analysis;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.VerifiedCitation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One line of the obligations checklist (api.md §5.10, FR-53). Until the verifier has run, {@code
 * claims} hold what the model proposed; only {@code citations} (all {@link VerifiedCitation}s) can
 * reach a user (R-01). The texts paraphrase the redacted contract, so they are {@link
 * SensitiveText} (R-02).
 *
 * @param party {@code EMPLOYER}, {@code WORKER} or {@code BOTH}
 * @param source {@code CONTRACT}, {@code STATUTE} or {@code BOTH}
 * @param status {@code STATED_IN_CONTRACT}, {@code STATUTORY_NOT_IN_CONTRACT} or {@code
 *     CONFLICTS_WITH_CONTRACT}
 */
public record ObligationItem(
    String id,
    String party,
    SensitiveText obligation,
    @Nullable SensitiveText trigger,
    @Nullable Deadline deadline,
    String source,
    String status,
    List<String> clauseIds,
    List<ClaimedCitation> claims,
    List<VerifiedCitation> citations) {

  /** A time limit; {@code unit} is DAYS, WEEKS or MONTHS. */
  public record Deadline(double value, String unit) {}

  public ObligationItem {
    clauseIds = List.copyOf(clauseIds);
    claims = List.copyOf(claims);
    citations = List.copyOf(citations);
  }

  /** True for an item that needs a verified citation to be shown. */
  public boolean needsCitation() {
    return !"CONTRACT".equals(source);
  }

  public ObligationItem verified(List<VerifiedCitation> accepted) {
    return new ObligationItem(
        id, party, obligation, trigger, deadline, source, status, clauseIds, List.of(), accepted);
  }
}
