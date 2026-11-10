package bd.shromikai.shared.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("NFR-04")
class RetryAfterTest {

  private final Instant now = Instant.parse("2026-11-11T10:00:00Z");

  @Test
  void shouldReadDelaySeconds() {
    assertThat(RetryAfter.seconds(" 30 ", now)).isEqualTo(30);
    assertThat(RetryAfter.seconds("0", now)).isZero();
  }

  @Test
  void shouldReadHttpDate_asSecondsFromNow() {
    assertThat(RetryAfter.seconds("Wed, 11 Nov 2026 10:01:30 GMT", now)).isEqualTo(90);
    assertThat(RetryAfter.seconds("Wed, 11 Nov 2026 09:59:00 GMT", now)).isZero();
  }

  @Test
  void shouldIgnoreMissingOrMalformedValues() {
    assertThat(RetryAfter.seconds(null, now)).isNull();
    assertThat(RetryAfter.seconds("", now)).isNull();
    assertThat(RetryAfter.seconds("-5", now)).isNull();
    assertThat(RetryAfter.seconds("soon", now)).isNull();
  }

  @Test
  void shouldPassOnlyFor429And503() {
    assertThat(RetryAfter.forStatus(429, "5", now)).isEqualTo(5);
    assertThat(RetryAfter.forStatus(503, "5", now)).isEqualTo(5);
    assertThat(RetryAfter.forStatus(500, "5", now)).isNull();
  }
}
