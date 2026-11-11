package bd.shromikai.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock with one a test can move (TTL, expiry). It starts at the real time
 * when the JVM starts and only moves forward when a test asks; tokens issued by the application use
 * it too, so a test that advances time must issue a new token afterwards.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfig {

  /** The one clock of the test JVM. */
  public static final MutableClock CLOCK = new MutableClock();

  /** A clock that moves only when told to. */
  public static final class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    public void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Bean
  @Primary
  Clock testClock() {
    return CLOCK;
  }
}
