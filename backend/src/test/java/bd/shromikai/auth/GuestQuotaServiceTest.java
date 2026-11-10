package bd.shromikai.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("FR-09")
class GuestQuotaServiceTest {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  /** 10:00 on 11 November in Dhaka. */
  private static final Instant START = Instant.parse("2026-11-11T04:00:00Z");

  private JwtServiceTest.MutableClock clock;
  private GuestQuotaService quotas;

  @BeforeEach
  void setUp() {
    clock = new JwtServiceTest.MutableClock(START);
    quotas = new GuestQuotaService(3, 10, 1, DHAKA, clock);
  }

  private static AuthenticatedSubject guest(String device) {
    return AuthenticatedSubject.guest(device.repeat(64).substring(0, 64));
  }

  @Test
  void shouldRefuseTheFourthAnalysisOfADevice_withRetryAfterMidnightInDhaka() {
    AuthenticatedSubject device = guest("a");
    for (int i = 0; i < 3; i++) {
      quotas.reserve(device, "10.0.0." + i).close();
    }

    assertThatThrownBy(() -> quotas.reserve(device, "10.0.0.9"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.type()).isEqualTo(ProblemType.QUOTA_EXCEEDED);
              // 10:00 to 24:00 in Dhaka.
              assertThat(e.retryAfterSeconds()).isEqualTo(14 * 3600);
            });
  }

  @Test
  void shouldRefuseTheEleventhAnalysisOfAnIp_acrossDevices() {
    for (int i = 0; i < 10; i++) {
      quotas.reserve(guest(Character.toString('a' + i)), "203.0.113.7").close();
    }

    assertThatThrownBy(() -> quotas.reserve(guest("z"), "203.0.113.7"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.QUOTA_EXCEEDED));
  }

  @Test
  void shouldAllowAgain_afterMidnightInDhaka() {
    AuthenticatedSubject device = guest("b");
    for (int i = 0; i < 3; i++) {
      quotas.reserve(device, "10.0.0.1").close();
    }

    clock.advance(Duration.ofHours(14).plusSeconds(1));

    quotas.reserve(device, "10.0.0.1").close();
  }

  @Test
  void shouldRefuseSecondConcurrentAnalysis_untilTheFirstIsReleased() {
    AuthenticatedSubject device = guest("c");
    GuestQuotaService.Reservation first = quotas.reserve(device, "10.0.0.1");

    assertThatThrownBy(() -> quotas.reserve(device, "10.0.0.1"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.CONCURRENCY_LIMIT));

    first.close();
    first.close(); // releasing twice must not free a slot that belongs to someone else
    quotas.reserve(device, "10.0.0.1").close();
  }

  @Test
  void shouldNotLimitSignedInUsers() {
    AuthenticatedSubject user =
        new AuthenticatedSubject(
            java.util.UUID.randomUUID(), Role.WORKER, null, false, false, null);
    for (int i = 0; i < 20; i++) {
      quotas.reserve(user, "10.0.0.1").close();
    }
  }
}
