package bd.shromikai.rules;

import org.jspecify.annotations.Nullable;

/**
 * One legal rule of {@code data/rules/*.yaml} (architecture §9.1). Only an {@code ACTIVE} rule is
 * ever evaluated; it is active only after a person other than its author reviewed it (FR-36).
 *
 * @param value the limit; {@code null} for {@code REQUIRED} and {@code PROHIBITED}
 * @param sectionKey the current section the legal basis resolved to at load time; {@code null} for
 *     a draft whose basis could not be resolved
 */
public record Rule(
    String id,
    String clauseType,
    FactParameter fact,
    RuleComparator comparator,
    @Nullable Double value,
    @Nullable Unit unit,
    @Nullable String appliesToCategory,
    LegalBasis legalBasis,
    Severity severity,
    String explanationBn,
    String explanationEn,
    String authoredBy,
    @Nullable String reviewedBy,
    @Nullable String reviewNote,
    Status status,
    @Nullable String sectionKey) {

  /** The statute passage a rule rests on, quoted verbatim from the corpus. */
  public record LegalBasis(String act, String section, String quote) {}

  public enum Status {
    DRAFT,
    ACTIVE
  }

  public boolean active() {
    return status == Status.ACTIVE;
  }

  /** The reviewer is named and is not the author. */
  public boolean twoPersonReviewed() {
    return reviewedBy != null
        && !reviewedBy.isBlank()
        && !reviewedBy.strip().equalsIgnoreCase(authoredBy.strip());
  }
}
