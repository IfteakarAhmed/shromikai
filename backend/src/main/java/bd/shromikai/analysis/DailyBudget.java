package bd.shromikai.analysis;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The daily cost cap over all analyses (FR-62, {@code shromikai.budget.daily-usd}). A new analysis
 * is refused with {@code budget-exceeded} once the Dhaka day's spend has reached the cap; every
 * finished or failed analysis adds its cost.
 */
@Component
public final class DailyBudget {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  private final DailySpendStore store;
  private final Clock clock;
  private final long capMicros;

  @Autowired
  DailyBudget(DailySpendStore store, Clock clock, AnalysisProperties properties) {
    this(store, clock, properties.budget().dailyUsdMicros());
  }

  DailyBudget(DailySpendStore store, Clock clock, long capMicros) {
    this.store = store;
    this.clock = clock;
    this.capMicros = capMicros;
  }

  /** Throws {@code budget-exceeded} when today's cap is spent. */
  public void admit() {
    if (remainingMicros() <= 0) {
      throw new ProblemException(ProblemType.BUDGET_EXCEEDED);
    }
  }

  public void record(long micros) {
    if (micros > 0) {
      store.add(today(), micros);
    }
  }

  public long remainingMicros() {
    return Math.max(0, capMicros - store.spentOn(today()));
  }

  private LocalDate today() {
    return clock.instant().atZone(DHAKA).toLocalDate();
  }
}
