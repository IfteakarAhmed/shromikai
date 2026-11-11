package bd.shromikai.rules;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a contract can state, with the units each fact may carry (api.md §6.4). These are fact types
 * read from a contract; they say nothing about what the law requires. Legal limits exist only in
 * {@code data/rules/*.yaml}, derived from the ingested statute (R-10).
 */
public enum FactParameter {
  PROBATION_LENGTH(Unit.MONTHS, Unit.DAYS),
  PROBATION_EXTENSION(Unit.MONTHS, Unit.DAYS),
  CONTRACT_DURATION(Unit.MONTHS),
  NOTICE_BY_EMPLOYER(Unit.DAYS, Unit.WEEKS),
  NOTICE_BY_WORKER(Unit.DAYS, Unit.WEEKS),
  PAY_IN_LIEU_OF_NOTICE_ALLOWED(Unit.BOOLEAN),
  DAILY_WORKING_HOURS(Unit.HOURS_PER_DAY),
  WEEKLY_WORKING_HOURS(Unit.HOURS_PER_WEEK),
  OVERTIME_RATE(Unit.MULTIPLIER),
  OVERTIME_DAILY_HOURS(Unit.HOURS_PER_DAY),
  OVERTIME_COMPULSORY(Unit.BOOLEAN),
  WEEKLY_REST_DAYS(Unit.COUNT),
  ANNUAL_LEAVE(Unit.DAYS),
  CASUAL_LEAVE(Unit.DAYS),
  SICK_LEAVE(Unit.DAYS),
  FESTIVAL_HOLIDAYS(Unit.DAYS),
  MATERNITY_LEAVE(Unit.DAYS, Unit.WEEKS),
  WAGE_PERIOD(Unit.DAYS),
  WAGE_PAYMENT_DEADLINE(Unit.DAYS),
  DEDUCTION_LIMIT(Unit.PERCENT),
  BASIC_WAGE(Unit.BDT_PER_MONTH),
  GROSS_WAGE(Unit.BDT_PER_MONTH),
  TERMINATION_WITHOUT_NOTICE_ALLOWED(Unit.BOOLEAN),
  TERMINATION_BENEFIT_STATED(Unit.BOOLEAN),
  NON_COMPETE_DURATION(Unit.MONTHS),
  DISPUTE_FORUM_STATED(Unit.BOOLEAN);

  private final Set<Unit> allowedUnits;

  FactParameter(Unit first, Unit... more) {
    this.allowedUnits = EnumSet.of(first, more);
  }

  public Set<Unit> allowedUnits() {
    return allowedUnits;
  }

  public boolean isBoolean() {
    return allowedUnits.contains(Unit.BOOLEAN);
  }
}
