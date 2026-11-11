package bd.shromikai.verify;

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
 */
public record VerificationContext(
    LocalDate analysisDate,
    Set<String> jurisdictions,
    String corpusRelease,
    Set<String> candidateKeys,
    int attempt) {

  public VerificationContext {
    jurisdictions = Set.copyOf(jurisdictions);
    candidateKeys = Set.copyOf(candidateKeys);
    if (attempt < 1 || attempt > 2) {
      throw new IllegalArgumentException("There is no attempt " + attempt);
    }
  }

  public VerificationContext forAttempt(int newAttempt, Set<String> newCandidates) {
    return new VerificationContext(
        analysisDate, jurisdictions, corpusRelease, newCandidates, newAttempt);
  }
}
