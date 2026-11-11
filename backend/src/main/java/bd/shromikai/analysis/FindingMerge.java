package bd.shromikai.analysis;

import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.ClaimedCitation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Merges rule findings with LLM findings (architecture §9.3, FR-49). For the same clause and the
 * same section key, one finding results: source {@code RULE_ENGINE}, the rule's citation, the LLM's
 * explanation and confidence plus 0.1 (cap 1.0). If the severities differ the rule's severity is
 * kept and the merge is reported as a conflict for a review item ({@code RULE_LLM_CONFLICT}). LLM
 * findings that repeat each other (same clauses, kind and first section key) are deduplicated.
 * Findings get their final IDs ({@code f1}, {@code f2}, ...), most severe first.
 */
@Component
public final class FindingMerge {

  static final double AGREEMENT_BONUS = 0.1;

  /** A merged finding whose severity the LLM judged differently. */
  public record Conflict(String findingId, Severity ruleSeverity, Severity llmSeverity) {}

  /** The merged list and the conflicts, keyed by the final finding IDs. */
  public record Result(List<Finding> findings, List<Conflict> conflicts) {}

  public Result merge(List<Finding> ruleFindings, List<Finding> llmFindings) {
    List<Finding> unmatched = new ArrayList<>(dedupe(llmFindings));
    List<Finding> combined = new ArrayList<>();
    List<String[]> conflictMarks = new ArrayList<>(); // {ruleId+clause, llmSeverity}
    java.util.Map<Finding, Severity> llmSeverities = new java.util.IdentityHashMap<>();
    for (Finding rule : ruleFindings) {
      Finding match = unmatched.stream().filter(l -> sameIssue(rule, l)).findFirst().orElse(null);
      if (match == null) {
        combined.add(rule);
        continue;
      }
      unmatched.remove(match);
      Finding merged =
          rule.merged(
              Math.min(1.0, rule.confidence() + AGREEMENT_BONUS),
              match.explanation(),
              rule.claims());
      combined.add(merged);
      if (match.severity() != rule.severity()) {
        llmSeverities.put(merged, match.severity());
      }
    }
    combined.addAll(unmatched);
    combined.sort(
        Comparator.comparing((Finding f) -> f.severity().ordinal())
            .thenComparing(f -> f.source() == FindingSource.RULE_ENGINE ? 0 : 1));
    List<Finding> numbered = new ArrayList<>();
    List<Conflict> conflicts = new ArrayList<>();
    for (int i = 0; i < combined.size(); i++) {
      Finding f = combined.get(i).withId("f" + (i + 1));
      numbered.add(f);
      Severity llm = llmSeverities.get(combined.get(i));
      if (llm != null) {
        conflicts.add(new Conflict(f.id(), f.severity(), llm));
      }
    }
    return new Result(numbered, conflicts);
  }

  private static boolean sameIssue(Finding rule, Finding llm) {
    String ruleKey = rule.claims().isEmpty() ? null : rule.claims().getFirst().sectionKey();
    boolean claimsRuleSection =
        ruleKey != null
            && llm.claims().stream().map(ClaimedCitation::sectionKey).anyMatch(ruleKey::equals);
    if (rule.clauseIds().isEmpty()) {
      // An omission: the LLM found the same missing protection.
      return llm.kind() == FindingKind.OMISSION
          && llm.clauseType().equals(rule.clauseType())
          && (claimsRuleSection || llm.claims().isEmpty());
    }
    return claimsRuleSection && llm.clauseIds().stream().anyMatch(rule.clauseIds()::contains);
  }

  private static List<Finding> dedupe(List<Finding> findings) {
    Set<String> seen = new HashSet<>();
    List<Finding> out = new ArrayList<>();
    List<Finding> sorted = new ArrayList<>(findings);
    sorted.sort(
        Comparator.comparing((Finding f) -> f.severity().ordinal())
            .thenComparing(f -> -f.confidence()));
    for (Finding f : sorted) {
      String key =
          String.join(",", f.clauseIds().stream().sorted().toList())
              + "|"
              + f.kind()
              + "|"
              + (f.claims().isEmpty() ? "" : f.claims().getFirst().sectionKey())
              + (f.claims().isEmpty() ? "|" + f.title().reveal() : "");
      if (seen.add(key)) {
        out.add(f);
      }
    }
    // Keep document order for findings that survived.
    out.sort(Comparator.comparingInt(findings::indexOf));
    return out;
  }

  /** Redacted text the merge keeps when the LLM explanation replaces the rule's. */
  static SensitiveText explanationOf(Finding f) {
    return f.explanation();
  }
}
