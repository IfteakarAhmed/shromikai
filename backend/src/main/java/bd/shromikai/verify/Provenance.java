package bd.shromikai.verify;

/**
 * The review record behind a {@code RULE} or {@code MAPPING} citation: only a source that is active
 * and was reviewed by a person other than its author earns {@code RULE_PROVENANCE} (architecture
 * §7.4, FR-36).
 */
public record Provenance(String sourceId, String authoredBy, String reviewedBy, boolean active) {

  /** Two-person review: a reviewer is named and is not the author. */
  public boolean twoPersonReviewed() {
    return active
        && reviewedBy != null
        && !reviewedBy.isBlank()
        && authoredBy != null
        && !reviewedBy.strip().equalsIgnoreCase(authoredBy.strip());
  }
}
