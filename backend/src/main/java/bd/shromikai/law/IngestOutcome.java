package bd.shromikai.law;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Result of one ingestion run (FR-15, FR-17). {@code status} is {@code SUCCEEDED} (a corpus release
 * was created) or {@code FAILED} (nothing was stored; the quality report names the reason).
 */
public record IngestOutcome(
    String status,
    @Nullable String corpusRelease,
    List<ActSummary> acts,
    List<String> failures,
    String reportDirectory) {

  /** What one act contributed to the run. */
  public record ActSummary(
      String code,
      String sourceKind,
      int sections,
      int newVersions,
      int unchanged,
      int withdrawn,
      @Nullable String failure) {}

  public boolean succeeded() {
    return status.equals("SUCCEEDED");
  }
}
