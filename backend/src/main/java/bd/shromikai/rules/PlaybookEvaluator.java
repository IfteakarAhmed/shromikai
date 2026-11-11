package bd.shromikai.rules;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Evaluates a playbook against the typed facts of the clauses (FR-57). The comparison is the one of
 * {@link RuleEngine}, applied per playbook rule to the clauses of the rule's type only, so a
 * deviation means exactly what a legal-rule violation means, but it is reported as a policy
 * deviation and carries the organisation's own message.
 */
@Component
public final class PlaybookEvaluator {

  /** The facts of one clause and its type. */
  public record TypedClause(String clauseId, String clauseType, List<ClauseFact> facts) {

    public TypedClause {
      facts = List.copyOf(facts);
    }
  }

  /** A playbook rule that a clause breaks, or a required fact that no clause of its type states. */
  public record Deviation(
      int ruleIndex,
      PlaybookRule rule,
      @Nullable String clauseId,
      @Nullable Double observed,
      @Nullable Unit observedUnit,
      boolean omission) {}

  private final RuleEngine engine;

  public PlaybookEvaluator(RuleEngine engine) {
    this.engine = engine;
  }

  public List<Deviation> evaluate(List<PlaybookRule> rules, List<TypedClause> clauses) {
    List<Deviation> out = new ArrayList<>();
    for (int i = 0; i < rules.size(); i++) {
      PlaybookRule rule = rules.get(i);
      List<RuleEngine.ClauseFacts> ofType =
          clauses.stream()
              .filter(c -> c.clauseType().equals(rule.clauseType()))
              .map(c -> new RuleEngine.ClauseFacts(c.clauseId(), c.facts()))
              .toList();
      RuleEngine.Evaluation evaluation = engine.evaluate(ofType, List.of(asRule(i, rule)), null);
      for (RuleViolation v : evaluation.violations()) {
        out.add(new Deviation(i, rule, v.clauseId(), v.observed(), v.observedUnit(), v.omission()));
      }
    }
    return out;
  }

  /** A playbook rule as an engine rule: active, no legal basis (it is policy, not law). */
  private static Rule asRule(int index, PlaybookRule rule) {
    return new Rule(
        "playbook-" + index,
        rule.clauseType(),
        rule.fact(),
        rule.comparator(),
        rule.value(),
        rule.unit(),
        null,
        new Rule.LegalBasis("", "", ""),
        rule.severity(),
        rule.messageBn(),
        rule.messageEn(),
        "playbook",
        "playbook",
        null,
        Rule.Status.ACTIVE,
        null);
  }
}
