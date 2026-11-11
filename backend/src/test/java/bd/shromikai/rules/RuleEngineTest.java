package bd.shromikai.rules;

import static bd.shromikai.rules.RulesFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.RuleEngine.ClauseFacts;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-36: the deterministic rule engine on extracted facts (fictional pack xx, R-10). */
@Tag("unit")
@Requirement("FR-36")
class RuleEngineTest {

  private final RuleEngine engine = new RuleEngine();

  private static final Rule NOTICE_MIN =
      rule(
          "xx-notice-min",
          FactParameter.NOTICE_BY_EMPLOYER,
          RuleComparator.MIN,
          14.0,
          Unit.DAYS,
          Rule.Status.ACTIVE);
  private static final Rule HOURS_MAX =
      rule(
          "xx-hours-max",
          FactParameter.WEEKLY_WORKING_HOURS,
          RuleComparator.MAX,
          40.0,
          Unit.HOURS_PER_WEEK,
          Rule.Status.ACTIVE);
  private static final Rule RATE_EQ =
      rule(
          "xx-rate-eq",
          FactParameter.OVERTIME_RATE,
          RuleComparator.EQ,
          2.0,
          Unit.MULTIPLIER,
          Rule.Status.ACTIVE);
  private static final Rule LEAVE_REQUIRED =
      rule(
          "xx-leave-required",
          FactParameter.ANNUAL_LEAVE,
          RuleComparator.REQUIRED,
          null,
          null,
          Rule.Status.ACTIVE);
  private static final Rule FORCED_OT =
      rule(
          "xx-overtime-forced",
          FactParameter.OVERTIME_COMPULSORY,
          RuleComparator.PROHIBITED,
          null,
          null,
          Rule.Status.ACTIVE);

  private static ClauseFact fact(FactParameter p, Double v, Unit u, double confidence) {
    return new ClauseFact(p, v, u, confidence, null);
  }

  static Stream<Arguments> cases() {
    return Stream.of(
        // rule, fact, expected violation?
        Arguments.of(
            "MIN below",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 7.0, Unit.DAYS, 0.9),
            true),
        Arguments.of(
            "MIN equal",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 14.0, Unit.DAYS, 0.9),
            false),
        Arguments.of(
            "MIN above",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 30.0, Unit.DAYS, 0.9),
            false),
        Arguments.of(
            "MIN weeks converted, below",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 1.0, Unit.WEEKS, 0.9),
            true),
        Arguments.of(
            "MIN weeks converted, equal",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 2.0, Unit.WEEKS, 0.9),
            false),
        Arguments.of(
            "MAX above",
            HOURS_MAX,
            fact(FactParameter.WEEKLY_WORKING_HOURS, 48.0, Unit.HOURS_PER_WEEK, 0.9),
            true),
        Arguments.of(
            "MAX equal",
            HOURS_MAX,
            fact(FactParameter.WEEKLY_WORKING_HOURS, 40.0, Unit.HOURS_PER_WEEK, 0.9),
            false),
        Arguments.of(
            "MAX below",
            HOURS_MAX,
            fact(FactParameter.WEEKLY_WORKING_HOURS, 35.0, Unit.HOURS_PER_WEEK, 0.9),
            false),
        Arguments.of(
            "EQ different",
            RATE_EQ,
            fact(FactParameter.OVERTIME_RATE, 1.5, Unit.MULTIPLIER, 0.9),
            true),
        Arguments.of(
            "EQ same",
            RATE_EQ,
            fact(FactParameter.OVERTIME_RATE, 2.0, Unit.MULTIPLIER, 0.9),
            false),
        Arguments.of(
            "PROHIBITED true",
            FORCED_OT,
            fact(FactParameter.OVERTIME_COMPULSORY, 1.0, Unit.BOOLEAN, 0.9),
            true),
        Arguments.of(
            "PROHIBITED false",
            FORCED_OT,
            fact(FactParameter.OVERTIME_COMPULSORY, 0.0, Unit.BOOLEAN, 0.9),
            false),
        Arguments.of(
            "value-less mention",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, null, Unit.DAYS, 0.9),
            false),
        Arguments.of(
            "low confidence skipped",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 1.0, Unit.DAYS, 0.59),
            false),
        Arguments.of(
            "confidence at the limit is evaluated",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_EMPLOYER, 1.0, Unit.DAYS, 0.60),
            true),
        Arguments.of(
            "other parameter ignored",
            NOTICE_MIN,
            fact(FactParameter.NOTICE_BY_WORKER, 1.0, Unit.DAYS, 0.9),
            false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void shouldFlagExactlyTheViolations(String name, Rule rule, ClauseFact fact, boolean violation) {
    RuleEngine.Evaluation result =
        engine.evaluate(List.of(new ClauseFacts("c1", List.of(fact))), List.of(rule), null);

    assertThat(result.violations()).hasSize(violation ? 1 : 0);
    if (violation) {
      assertThat(result.violations().getFirst().clauseId()).isEqualTo("c1");
      assertThat(result.violations().getFirst().omission()).isFalse();
    }
  }

  @Test
  void shouldRecordALowConfidenceNote() {
    RuleEngine.Evaluation result =
        engine.evaluate(
            List.of(
                new ClauseFacts(
                    "c1", List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 1.0, Unit.DAYS, 0.3)))),
            List.of(NOTICE_MIN),
            null);

    assertThat(result.notes())
        .extracting(RuleEngine.Note::code)
        .containsExactly("rules.low_confidence");
  }

  @Test
  void shouldSkipAMonthsFactAgainstADaysRuleAndRecordTheMismatch() {
    ClauseFact months =
        new ClauseFact(FactParameter.NOTICE_BY_EMPLOYER, 1.0, Unit.WEEKS, 0.9, null);
    Rule monthsRule =
        rule(
            "xx-w",
            FactParameter.PROBATION_LENGTH,
            RuleComparator.MAX,
            90.0,
            Unit.DAYS,
            Rule.Status.ACTIVE);
    ClauseFact probationMonths =
        new ClauseFact(FactParameter.PROBATION_LENGTH, 3.0, Unit.MONTHS, 0.9, null);

    RuleEngine.Evaluation result =
        engine.evaluate(
            List.of(new ClauseFacts("c1", List.of(months, probationMonths))),
            List.of(monthsRule),
            null);

    assertThat(result.violations()).isEmpty();
    assertThat(result.notes())
        .extracting(RuleEngine.Note::code)
        .containsExactly("rules.unit_mismatch");
  }

  @Test
  void shouldReportAnOmission_whenNoClauseStatesARequiredFact() {
    RuleEngine.Evaluation result =
        engine.evaluate(
            List.of(
                new ClauseFacts(
                    "c1", List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 30.0, Unit.DAYS, 0.9)))),
            List.of(LEAVE_REQUIRED),
            null);

    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().getFirst().omission()).isTrue();
    assertThat(result.violations().getFirst().clauseId()).isNull();
  }

  @Test
  void shouldNotReportAnOmission_whenARequiredFactIsPresentEvenWithLowConfidenceOrNoValue() {
    for (ClauseFact present :
        List.of(
            fact(FactParameter.ANNUAL_LEAVE, 20.0, Unit.DAYS, 0.9),
            fact(FactParameter.ANNUAL_LEAVE, null, Unit.DAYS, 0.9),
            fact(FactParameter.ANNUAL_LEAVE, 1.0, Unit.DAYS, 0.2))) {
      assertThat(
              engine
                  .evaluate(
                      List.of(new ClauseFacts("c1", List.of(present))),
                      List.of(LEAVE_REQUIRED),
                      null)
                  .violations())
          .isEmpty();
    }
  }

  @Test
  void shouldNeverEvaluateADraftRule() {
    Rule draft =
        rule(
            "xx-draft",
            FactParameter.NOTICE_BY_EMPLOYER,
            RuleComparator.MIN,
            14.0,
            Unit.DAYS,
            Rule.Status.DRAFT);

    assertThat(
            engine
                .evaluate(
                    List.of(
                        new ClauseFacts(
                            "c1",
                            List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 1.0, Unit.DAYS, 0.9)))),
                    List.of(draft),
                    null)
                .violations())
        .isEmpty();
  }

  @Test
  void shouldApplyACategoryRuleOnlyToThatCategory() {
    Rule categorised =
        new Rule(
            "xx-cat",
            "OTHER",
            FactParameter.NOTICE_BY_EMPLOYER,
            RuleComparator.MIN,
            30.0,
            Unit.DAYS,
            "PERMANENT",
            NOTICE_MIN.legalBasis(),
            Severity.HIGH,
            "কাল্পনিক ব্যাখ্যা",
            "Fictional",
            "author@example.test",
            "reviewer@example.test",
            null,
            Rule.Status.ACTIVE,
            "XX-LAB:1@original");
    List<ClauseFacts> clauses =
        List.of(
            new ClauseFacts(
                "c1", List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 14.0, Unit.DAYS, 0.9))));

    assertThat(engine.evaluate(clauses, List.of(categorised), "PERMANENT").violations()).hasSize(1);
    assertThat(engine.evaluate(clauses, List.of(categorised), "TEMPORARY").violations()).isEmpty();
    assertThat(engine.evaluate(clauses, List.of(categorised), null).violations()).isEmpty();
  }

  @Test
  void shouldReportOneViolationPerClauseThatBreaksTheRule() {
    List<ClauseFacts> clauses =
        List.of(
            new ClauseFacts(
                "c1", List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 3.0, Unit.DAYS, 0.9))),
            new ClauseFacts(
                "c2", List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 30.0, Unit.DAYS, 0.9))),
            new ClauseFacts(
                "c3", List.of(fact(FactParameter.NOTICE_BY_EMPLOYER, 5.0, Unit.DAYS, 0.9))));

    assertThat(engine.evaluate(clauses, List.of(NOTICE_MIN), null).violations())
        .extracting(RuleViolation::clauseId)
        .containsExactly("c1", "c3");
  }

  @Test
  void shouldRefuseAFactWithAUnitThatDoesNotBelongToItsParameter() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ClauseFact(FactParameter.ANNUAL_LEAVE, 1.0, Unit.PERCENT, 0.9, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
