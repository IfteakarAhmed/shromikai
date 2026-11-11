package bd.shromikai.analysis;

import bd.shromikai.rules.Rule;
import bd.shromikai.rules.RuleViolation;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.verify.CitationOrigin;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.Provenance;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns rule violations into findings (architecture §9.2): a {@code CONFLICT}, or an {@code
 * OMISSION} for a required fact nobody states, with the rule's severity, its explanation in the
 * output language and a claimed citation built from the rule's legal basis. The citation still goes
 * through the verifier (existence and quote); its support check is the rule's two-person review.
 */
final class RuleFindings {

  /** Confidence of a rule finding before the LLM agrees (FR-49 adds 0.1 on agreement). */
  static final double BASE_CONFIDENCE = 0.85;

  private RuleFindings() {}

  static List<Finding> of(List<RuleViolation> violations, String language) {
    List<Finding> out = new ArrayList<>();
    int n = 0;
    for (RuleViolation v : violations) {
      Rule rule = v.rule();
      String explanation = "bn".equals(language) ? rule.explanationBn() : rule.explanationEn();
      List<ClaimedCitation> claims = List.of();
      if (rule.sectionKey() != null) {
        claims =
            List.of(
                ClaimedCitation.of(
                    rule.sectionKey(),
                    rule.legalBasis().quote(),
                    SensitiveText.of(explanation),
                    language,
                    CitationOrigin.RULE,
                    rule.id(),
                    new Provenance(
                        rule.id(), rule.authoredBy(), rule.reviewedBy(), rule.active())));
      }
      out.add(
          new Finding(
              "r" + (++n),
              SensitiveText.of(title(explanation)),
              v.omission() ? FindingKind.OMISSION : FindingKind.CONFLICT,
              rule.severity(),
              v.omission(),
              FindingSource.RULE_ENGINE,
              rule.clauseType(),
              v.clauseId() == null ? List.of() : List.of(v.clauseId()),
              SensitiveText.of(explanation),
              SensitiveText.of(""),
              BASE_CONFIDENCE,
              rule.id(),
              claims,
              FindingVerification.UNCERTAIN,
              List.of(),
              List.of(),
              null,
              null));
    }
    return out;
  }

  /** The first sentence of the explanation, at most 120 characters (api.md §5.8 title). */
  static String title(String explanation) {
    String first = explanation.strip();
    int end = first.length();
    for (char stop : new char[] {'.', '।', ';'}) {
      int i = first.indexOf(stop);
      if (i > 4 && i < end) {
        end = i;
      }
    }
    String title = first.substring(0, Math.min(end, 120)).strip();
    return title.length() < 5 ? first.substring(0, Math.min(first.length(), 120)) : title;
  }
}
