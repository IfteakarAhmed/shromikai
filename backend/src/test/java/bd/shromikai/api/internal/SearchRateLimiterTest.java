package bd.shromikai.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-22 / api.md §8.1: law search is limited per subject and minute. */
@Tag("unit")
@Requirement("FR-22")
class SearchRateLimiterTest {

  private static AuthenticatedSubject user() {
    return new AuthenticatedSubject(UUID.randomUUID(), Role.WORKER, null, false, false, null);
  }

  @Test
  void shouldAllowTheLimitThenReturnRateLimitedWithRetryAfter() {
    SearchRateLimiter limiter = new SearchRateLimiter(new ApiProperties(2));
    AuthenticatedSubject subject = user();

    limiter.check(subject);
    limiter.check(subject);

    assertThatThrownBy(() -> limiter.check(subject))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.type()).isEqualTo(ProblemType.RATE_LIMITED);
              assertThat(e.retryAfterSeconds()).isBetween(1, 60);
            });
  }

  @Test
  void shouldCountSubjectsSeparatelyAndGuestsByDevice() {
    SearchRateLimiter limiter = new SearchRateLimiter(new ApiProperties(1));

    limiter.check(user());
    limiter.check(user());
    limiter.check(AuthenticatedSubject.guest("a".repeat(64)));
    limiter.check(AuthenticatedSubject.guest("b".repeat(64)));
    assertThatThrownBy(() -> limiter.check(AuthenticatedSubject.guest("a".repeat(64))))
        .isInstanceOf(ProblemException.class);
  }
}
