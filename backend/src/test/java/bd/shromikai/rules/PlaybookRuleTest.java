package bd.shromikai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.PlaybookEvaluator.Deviation;
import bd.shromikai.rules.PlaybookEvaluator.TypedClause;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-57: a deviation is flagged; the unit must fit the fact; the type scopes the rule. */
@Tag("unit")
@Requirement("FR-57")
class PlaybookRuleTest {

  private static final Set<String> TYPES = Set.of("PROBATION", "WAGES", "NOTICE_AND_RESIGNATION");
  private final PlaybookEvaluator evaluator = new PlaybookEvaluator(new RuleEngine());
  private final PlaybookValidator validator = new PlaybookValidator();

  private static PlaybookRule probationMax3Months() {
    return new PlaybookRule(
        "PROBATION",
        FactParameter.PROBATION_LENGTH,
        RuleComparator.MAX,
        3.0,
        Unit.MONTHS,
        Severity.MEDIUM,
        "আমাদের নীতিতে শিক্ষানবিশকাল সর্বোচ্চ ৩ মাস।",
        "Our standard probation is 3 months.",
        null);
  }

  private static TypedClause probation(double months) {
    return new TypedClause(
        "c1",
        "PROBATION",
        List.of(new ClauseFact(FactParameter.PROBATION_LENGTH, months, Unit.MONTHS, 0.9, null)));
  }

  @Test
  void aProbationLongerThanThePlaybookLimitIsADeviation() {
    List<Deviation> deviations =
        evaluator.evaluate(List.of(probationMax3Months()), List.of(probation(6)));

    assertThat(deviations).hasSize(1);
    assertThat(deviations.get(0).clauseId()).isEqualTo("c1");
    assertThat(deviations.get(0).observed()).isEqualTo(6.0);
    assertThat(deviations.get(0).rule().messageEn())
        .isEqualTo("Our standard probation is 3 months.");
  }

  @Test
  void aProbationWithinTheLimitIsNotADeviation() {
    assertThat(evaluator.evaluate(List.of(probationMax3Months()), List.of(probation(3)))).isEmpty();
  }

  @Test
  void aRuleOnlyLooksAtClausesOfItsOwnType() {
    TypedClause wages =
        new TypedClause(
            "c2",
            "WAGES",
            List.of(new ClauseFact(FactParameter.PROBATION_LENGTH, 12.0, Unit.MONTHS, 0.9, null)));

    assertThat(evaluator.evaluate(List.of(probationMax3Months()), List.of(wages))).isEmpty();
  }

  @Test
  void aRequiredFactMissingFromEveryClauseOfItsTypeIsAnOmissionDeviation() {
    PlaybookRule required =
        new PlaybookRule(
            "NOTICE_AND_RESIGNATION",
            FactParameter.NOTICE_BY_WORKER,
            RuleComparator.REQUIRED,
            null,
            null,
            Severity.LOW,
            "বার্তা",
            "Notice by the worker must be stated.",
            null);

    List<Deviation> deviations = evaluator.evaluate(List.of(required), List.of(probation(3)));

    assertThat(deviations).singleElement().satisfies(d -> assertThat(d.omission()).isTrue());
  }

  @Test
  void anIncompatibleUnitIsReportedAtThePointerOfTheUnit() {
    PlaybookRule bad =
        new PlaybookRule(
            "PROBATION",
            FactParameter.PROBATION_LENGTH,
            RuleComparator.MAX,
            3.0,
            Unit.HOURS_PER_DAY,
            Severity.LOW,
            "বার্তা",
            "msg",
            null);

    List<FieldError> errors =
        validator.validate(
            new PlaybookInput("Standard", "EMPLOYER", List.of(probationMax3Months(), bad)), TYPES);

    assertThat(errors).extracting(FieldError::pointer).containsExactly("/rules/1/unit");
  }

  @Test
  void aValidPlaybookHasNoErrorsAndValueRulesFollowTheComparator() {
    assertThat(
            validator.validate(
                new PlaybookInput(
                    "Standard staff contract", "EMPLOYER", List.of(probationMax3Months())),
                TYPES))
        .isEmpty();
    PlaybookRule prohibitedWithValue =
        new PlaybookRule(
            "WAGES",
            FactParameter.OVERTIME_COMPULSORY,
            RuleComparator.PROHIBITED,
            1.0,
            null,
            Severity.HIGH,
            "বার্তা",
            "msg",
            null);
    assertThat(
            validator.validate(
                new PlaybookInput("Standard", "WORKER", List.of(prohibitedWithValue)), TYPES))
        .extracting(FieldError::pointer)
        .containsExactly("/rules/0/value");
  }

  @Test
  void sizesAndSidesAreChecked() {
    List<FieldError> errors = validator.validate(new PlaybookInput("x", "BOTH", List.of()), TYPES);

    assertThat(errors)
        .extracting(FieldError::pointer)
        .containsExactlyInAnyOrder("/name", "/side", "/rules");
  }
}
