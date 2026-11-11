package bd.shromikai.analysis;

import java.time.LocalDate;

/** Money spent on model calls per day, in micro-US-dollars (no contract data, R-02). */
interface DailySpendStore {

  /** Adds to the day's total and returns the new total. */
  long add(LocalDate day, long micros);

  long spentOn(LocalDate day);
}
