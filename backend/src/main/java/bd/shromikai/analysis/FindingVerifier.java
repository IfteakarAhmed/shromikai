package bd.shromikai.analysis;

import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.ReasonCode;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerificationStatus;
import bd.shromikai.verify.VerifiedCitation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Step {@code VERIFY} (architecture §7): every claimed citation of every finding goes through the
 * {@link CitationVerifier} in parallel. A finding with at least one {@code VERIFIED} citation keeps
 * only the verified ones (at most four) and is {@code VERIFIED}; any other finding is {@code
 * UNCERTAIN}, shows no citation and keeps its claims for the corrective loop (R-01).
 */
@Component
public class FindingVerifier {

  /** Content-free record of one verdict for the trace. */
  public record VerdictSummary(
      String findingId,
      String sectionKey,
      VerificationStatus status,
      List<String> reasonCodes,
      int attempt,
      String supportLabel,
      Double supportConfidence,
      String supportReason) {

    /** The summary of one verdict; the verifier's one-line reason stays in memory only. */
    public static VerdictSummary of(String findingId, Verdict verdict) {
      var support = verdict.result().checks().support();
      return new VerdictSummary(
          findingId,
          verdict.result().sectionKey(),
          verdict.result().status(),
          verdict.result().reasonCodes().stream().map(ReasonCode::name).toList(),
          verdict.result().attempt(),
          support.label() == null ? null : support.label().name(),
          support.confidence(),
          support.reason());
    }
  }

  /** Findings after verification and the verdicts that led there. */
  public record Round(List<Finding> findings, List<VerdictSummary> verdicts) {}

  /** The reason code of a finding for which no citation was proposed at all. */
  public static final String NO_CITATION = "NO_CITATION_PROPOSED";

  private final CitationVerifier verifier;

  public FindingVerifier(CitationVerifier verifier) {
    this.verifier = verifier;
  }

  public Round verify(List<Finding> findings, VerificationContext context) {
    List<ClaimedCitation> claims = new ArrayList<>();
    for (Finding finding : findings) {
      claims.addAll(finding.claims());
    }
    Map<UUID, Verdict> verdicts =
        verifier.verifyAll(claims, context).stream()
            .collect(Collectors.toMap(v -> v.result().citationId(), Function.identity()));
    List<Finding> out = new ArrayList<>();
    List<VerdictSummary> summaries = new ArrayList<>();
    for (Finding finding : findings) {
      List<VerifiedCitation> verified = new ArrayList<>();
      LinkedHashSet<String> reasons = new LinkedHashSet<>();
      for (ClaimedCitation claim : finding.claims()) {
        Verdict verdict = verdicts.get(claim.citationId());
        summaries.add(VerdictSummary.of(finding.id(), verdict));
        if (verdict.verified() && verified.size() < 4) {
          verified.add(verdict.citation());
        } else {
          verdict.result().reasonCodes().forEach(r -> reasons.add(r.name()));
        }
      }
      if (!verified.isEmpty()) {
        out.add(finding.verified(verified));
      } else {
        if (finding.claims().isEmpty()) {
          reasons.add(NO_CITATION);
        }
        out.add(finding.uncertain(new ArrayList<>(reasons)));
      }
    }
    return new Round(out, summaries);
  }
}
