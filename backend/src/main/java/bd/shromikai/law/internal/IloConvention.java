package bd.shromikai.law.internal;

import org.jspecify.annotations.Nullable;

/**
 * An ILO convention proposed for a pack. Only conventions with a reviewer different from the
 * proposer are ingested (FR-12). {@code textFile} and {@code ratificationFile} are the NORMLEX
 * pages a person saved under data/raw/ilo/.
 */
record IloConvention(
    String code,
    String titleLabel,
    String normlexUrl,
    String proposedBy,
    @Nullable String reviewedBy,
    String textFile,
    String ratificationFile) {

  boolean reviewed() {
    return reviewedBy != null
        && !reviewedBy.isBlank()
        && !reviewedBy.strip().equalsIgnoreCase(proposedBy.strip());
  }
}
