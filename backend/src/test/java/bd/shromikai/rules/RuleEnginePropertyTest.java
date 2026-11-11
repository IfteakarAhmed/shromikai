package bd.shromikai.rules;

import static bd.shromikai.rules.RulesFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.RuleEngine.ClauseFacts;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/** Properties of the rule engine (test-strategy.md §8). */
@Tag("unit")
@Requirement("FR-36")
class RuleEnginePropertyTest {

  private final RuleEngine engine = new RuleEngine();

  @Provide
  Arbitrary<Double> numbers() {
    return Arbitraries.integers().between(0, 500).map(Integer::doubleValue);
  }

  private boolean violates(Rule rule, FactParameter p, double value, Unit unit) {
    return !engine
        .evaluate(
            List.of(new ClauseFacts("c1", List.of(new ClauseFact(p, value, unit, 0.9, null)))),
            List.of(rule),
            null)
        .violations()
        .isEmpty();
  }

  @Property
  void minPassesForEveryLargerValue(
      @ForAll("numbers") double limit,
      @ForAll("numbers") double v,
      @ForAll("numbers") double extra) {
    Rule min =
        rule(
            "xx-min",
            FactParameter.CASUAL_LEAVE,
            RuleComparator.MIN,
            limit,
            Unit.DAYS,
            Rule.Status.ACTIVE);

    if (!violates(min, FactParameter.CASUAL_LEAVE, v, Unit.DAYS)) {
      assertThat(violates(min, FactParameter.CASUAL_LEAVE, v + extra, Unit.DAYS)).isFalse();
    }
  }

  @Property
  void maxPassesForEverySmallerValue(
      @ForAll("numbers") double limit,
      @ForAll("numbers") double v,
      @ForAll("numbers") double less) {
    Rule max =
        rule(
            "xx-max",
            FactParameter.WEEKLY_WORKING_HOURS,
            RuleComparator.MAX,
            limit,
            Unit.HOURS_PER_WEEK,
            Rule.Status.ACTIVE);

    if (!violates(max, FactParameter.WEEKLY_WORKING_HOURS, v, Unit.HOURS_PER_WEEK)) {
      assertThat(
              violates(
                  max,
                  FactParameter.WEEKLY_WORKING_HOURS,
                  Math.max(0, v - less),
                  Unit.HOURS_PER_WEEK))
          .isFalse();
    }
  }

  @Property
  void weeksAndDaysAgree(@ForAll("numbers") double limitWeeks, @ForAll("numbers") double stated) {
    Rule inDays =
        rule(
            "xx-d",
            FactParameter.NOTICE_BY_EMPLOYER,
            RuleComparator.MIN,
            limitWeeks * 7,
            Unit.DAYS,
            Rule.Status.ACTIVE);
    Rule inWeeks =
        rule(
            "xx-w",
            FactParameter.NOTICE_BY_EMPLOYER,
            RuleComparator.MIN,
            limitWeeks,
            Unit.WEEKS,
            Rule.Status.ACTIVE);

    assertThat(violates(inDays, FactParameter.NOTICE_BY_EMPLOYER, stated, Unit.WEEKS))
        .isEqualTo(violates(inWeeks, FactParameter.NOTICE_BY_EMPLOYER, stated, Unit.WEEKS));
    assertThat(violates(inDays, FactParameter.NOTICE_BY_EMPLOYER, stated * 7, Unit.DAYS))
        .isEqualTo(violates(inDays, FactParameter.NOTICE_BY_EMPLOYER, stated, Unit.WEEKS));
  }

  @Property
  void aUnitMismatchNeverYieldsAViolation(
      @ForAll("numbers") double limit, @ForAll("numbers") double stated) {
    // Months never convert to days, so the rule is skipped whatever the numbers are.
    Rule days =
        rule(
            "xx-p",
            FactParameter.PROBATION_LENGTH,
            RuleComparator.MAX,
            limit,
            Unit.DAYS,
            Rule.Status.ACTIVE);

    assertThat(violates(days, FactParameter.PROBATION_LENGTH, stated, Unit.MONTHS)).isFalse();
  }
}
