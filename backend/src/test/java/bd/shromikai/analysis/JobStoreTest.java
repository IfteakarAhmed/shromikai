package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** NFR-20, R-02: jobs expire without any request arriving, and their number is capped. */
@Tag("unit")
@Requirement("NFR-20")
class JobStoreTest {

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-11T10:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void theSweepPurgesExpiredJobsWithoutAnyLookup() {
    var clock = new MutableClock();
    var store = new JobStore(clock, 10);
    AnalysisJob old = store.create();
    clock.advance(Duration.ofMinutes(20));
    store.create();
    clock.advance(Duration.ofMinutes(11));

    store.purgeExpired(); // what the scheduled sweep calls

    assertThat(store.size()).isEqualTo(1);
    assertThat(store.find(old.id())).isEmpty();
  }

  @Test
  void aFullStoreRefusesNewJobsAndAcceptsThemAgainAfterExpiry() {
    var clock = new MutableClock();
    var store = new JobStore(clock, 2);
    store.create();
    store.create();

    assertThatThrownBy(store::create)
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.CAPACITY_EXHAUSTED));

    clock.advance(JobStore.TTL.plusSeconds(1));
    assertThat(store.create()).isNotNull();
    assertThat(store.size()).isEqualTo(1);
  }
}
