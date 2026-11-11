package bd.shromikai.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The deterministic rule engine (architecture §9.2, FR-36): no AI, no I/O. Facts and active rules
 * go in, violations and content-free notes come out.
 */
@Component
public final class RuleEngine {

  /** Facts below this confidence are not evaluated (architecture §9.2). */
  public static final double MIN_CONFIDENCE = 0.60;

  /** The facts of one clause. */
  public record ClauseFacts(String clauseId, List<ClauseFact> facts) {

    public ClauseFacts {
      facts = List.copyOf(facts);
    }
  }

  /** A skipped evaluation; the codes go to the trace ({@code rules.unit_mismatch}, ...). */
  public record Note(String code, String ruleId, String clauseId) {}

  /** The outcome of one evaluation. */
  public record Evaluation(List<RuleViolation> violations, List<Note> notes) {

    public Evaluation {
      violations = List.copyOf(violations);
      notes = List.copyOf(notes);
    }
  }

  /**
   * @param workerCategory the contract's worker category, or {@code null}; a rule with {@code
   *     applies_to_category} runs only when it equals this
   */
  public Evaluation evaluate(
      List<ClauseFacts> clauses, List<Rule> rules, @Nullable String workerCategory) {
    List<RuleViolation> violations = new ArrayList<>();
    List<Note> notes = new ArrayList<>();
    for (Rule rule : rules) {
      if (!rule.active()
          || (rule.appliesToCategory() != null
              && !rule.appliesToCategory().equals(workerCategory))) {
        continue;
      }
      boolean present = false;
      for (ClauseFacts clause : clauses) {
        for (ClauseFact fact : clause.facts()) {
          if (fact.parameter() != rule.fact()) {
            continue;
          }
          present = true;
          if (rule.comparator() == RuleComparator.REQUIRED) {
            continue;
          }
          if (fact.confidence() < MIN_CONFIDENCE) {
            notes.add(new Note("rules.low_confidence", rule.id(), clause.clauseId()));
            continue;
          }
          check(rule, clause.clauseId(), fact, violations, notes);
        }
      }
      if (rule.comparator() == RuleComparator.REQUIRED && !present) {
        violations.add(new RuleViolation(rule, null, null, null, true));
      }
    }
    return new Evaluation(violations, notes);
  }

  private static void check(
      Rule rule,
      String clauseId,
      ClauseFact fact,
      List<RuleViolation> violations,
      List<Note> notes) {
    if (fact.value() == null) {
      return; // mentioned without a value: nothing to compare
    }
    double observed;
    if (rule.comparator() == RuleComparator.PROHIBITED) {
      observed = fact.value();
      if (observed == 1.0) {
        violations.add(new RuleViolation(rule, clauseId, observed, fact.unit(), false));
      }
      return;
    }
    Double comparable = comparable(fact.value(), fact.unit(), Objects.requireNonNull(rule.unit()));
    if (comparable == null) {
      notes.add(new Note("rules.unit_mismatch", rule.id(), clauseId));
      return;
    }
    double limit = Objects.requireNonNull(rule.value());
    boolean violated =
        switch (rule.comparator()) {
          case MIN -> comparable < limit;
          case MAX -> comparable > limit;
          case EQ -> comparable != limit;
          case REQUIRED, PROHIBITED -> false;
        };
    if (violated) {
      violations.add(new RuleViolation(rule, clauseId, fact.value(), fact.unit(), false));
    }
  }

  /**
   * The value in the rule's unit, or {@code null} when the units cannot be compared. Only weeks and
   * days convert (1 week = 7 days); months, per-day and per-week hours never do.
   */
  static @Nullable Double comparable(double value, Unit from, Unit to) {
    if (from == to) {
      return value;
    }
    if (from == Unit.WEEKS && to == Unit.DAYS) {
      return value * 7;
    }
    if (from == Unit.DAYS && to == Unit.WEEKS) {
      return value / 7;
    }
    return null;
  }
}
