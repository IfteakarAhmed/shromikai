package bd.shromikai.verify.internal;

import bd.shromikai.law.LawReadService.SectionLookup;
import bd.shromikai.verify.CitationOrigin;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.ReasonCode;
import bd.shromikai.verify.VerificationContext;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Check (a), existence and currency (architecture §7.3, FR-42): the key resolves to a current
 * section of the claimed act, the act is in force and in the pack, the section is effective on the
 * analysis date and, for LLM-origin citations, the key is in the candidate set.
 */
public final class ExistenceCheck {

  /** The outcome: a failure reason, or the section found. */
  public record Outcome(@Nullable ReasonCode failure, @Nullable SectionLookup lookup) {

    public boolean passed() {
      return failure == null;
    }
  }

  private final SectionLookupPort sections;

  public ExistenceCheck(SectionLookupPort sections) {
    this.sections = sections;
  }

  public Outcome check(ClaimedCitation claim, VerificationContext context) {
    Optional<SectionLookup> found = sections.find(claim.sectionKey());
    if (found.isEmpty()) {
      return fail(ReasonCode.SECTION_NOT_FOUND);
    }
    SectionLookup lookup = found.get();
    if (!claim.actCode().isBlank() && !claim.actCode().equals(lookup.section().actCode())) {
      return fail(ReasonCode.ACT_MISMATCH);
    }
    if (!lookup.section().isCurrent()
        || !"IN_FORCE".equals(lookup.actStatus())
        || (lookup.section().effectiveFrom() != null
            && lookup.section().effectiveFrom().isAfter(context.analysisDate()))) {
      return fail(ReasonCode.SECTION_NOT_CURRENT);
    }
    if (!context.jurisdictions().contains(lookup.jurisdiction())) {
      return fail(ReasonCode.ACT_NOT_IN_PACK);
    }
    if (claim.origin() == CitationOrigin.LLM
        && !context.candidateKeys().contains(claim.sectionKey())) {
      return fail(ReasonCode.NOT_IN_CANDIDATES);
    }
    return new Outcome(null, lookup);
  }

  private static Outcome fail(ReasonCode reason) {
    return new Outcome(reason, null);
  }
}
