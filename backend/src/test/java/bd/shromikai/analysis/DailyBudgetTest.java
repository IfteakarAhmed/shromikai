package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-62: the daily cap stops new analyses with capacity-exhausted; a new Dhaka day starts at zero.
 */
@Tag("unit")
@Requirement("FR-62")
class DailyBudgetTest {

  private static final class MemoryStore implements DailySpendStore {
    final Map<java.time.LocalDate, Long> spent = new HashMap<>();

    @Override
    public long add(java.time.LocalDate day, long micros) {
      return spent.merge(day, micros, Long::sum);
    }

    @Override
    public long spentOn(java.time.LocalDate day) {
      return spent.getOrDefault(day, 0L);
    }
  }

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  @Test
  void anAnalysisIsRefusedOnceTheDayIsSpent() {
    var store = new MemoryStore();
    var budget = new DailyBudget(store, at("2026-11-11T10:00:00Z"), 2_000_000);

    budget.admit();
    budget.record(2_000_000);

    assertThat(budget.remainingMicros()).isZero();
    assertThatThrownBy(budget::admit)
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.CAPACITY_EXHAUSTED));
  }

  @Test
  void theDayRollsOverAtMidnightInDhaka() {
    var store = new MemoryStore();
    new DailyBudget(store, at("2026-11-11T17:00:00Z"), 1_000_000).record(1_000_000); // 23:00 Dhaka

    var nextDay = new DailyBudget(store, at("2026-11-11T18:30:00Z"), 1_000_000); // 00:30 Dhaka

    nextDay.admit(); // does not throw
    assertThat(nextDay.remainingMicros()).isEqualTo(1_000_000);
  }
}
