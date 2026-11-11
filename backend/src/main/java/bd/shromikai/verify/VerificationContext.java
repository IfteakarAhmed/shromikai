package bd.shromikai.verify;

import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.text.OverlapChecker;
import java.time.LocalDate;
import java.util.Set;

/**
 * What the verifier needs to know about the analysis (architecture §7.1).
 *
 * @param analysisDate the date the analysis (or an external call) runs on
 * @param jurisdictions the jurisdictions of the pack, for example {@code BD} (and {@code ILO})
 * @param corpusRelease the corpus release label recorded in every result
 * @param candidateKeys section keys the risk agent could cite; empty for non-LLM origins
 * @param attempt 1, or 2 in the corrective loop
 * @param egress the job's egress context for every cloud call the verifier makes (R-03)
 * @param document index of the redacted document, to keep contract text out of the claim sent to
 *     the verifier (R-02, R-03)
 */
public record VerificationContext(
    LocalDate analysisDate,
    Set<String> jurisdictions,
    String corpusRelease,
    Set<String> candidateKeys,
    int attempt,
    EgressContext egress,
    OverlapChecker document) {

  /** For callers outside an analysis (external citation checks): no job values, no document. */
  public VerificationContext(
      LocalDate analysisDate,
      Set<String> jurisdictions,
      String corpusRelease,
      Set<String> candidateKeys,
      int attempt) {
    this(
        analysisDate,
        jurisdictions,
        corpusRelease,
        candidateKeys,
        attempt,
        EgressContext.NONE,
        OverlapChecker.of());
  }

  public VerificationContext {
    jurisdictions = Set.copyOf(jurisdictions);
    candidateKeys = Set.copyOf(candidateKeys);
    if (attempt < 1 || attempt > 2) {
      throw new IllegalArgumentException("There is no attempt " + attempt);
    }
  }

  public VerificationContext forAttempt(int newAttempt, Set<String> newCandidates) {
    return new VerificationContext(
        analysisDate, jurisdictions, corpusRelease, newCandidates, newAttempt, egress, document);
  }
}
