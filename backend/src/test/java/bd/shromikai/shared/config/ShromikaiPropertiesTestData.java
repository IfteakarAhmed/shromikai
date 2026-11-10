package bd.shromikai.shared.config;

import java.time.Duration;
import java.time.ZoneId;

/** Fictional identity settings for tests that build {@link ShromikaiProperties} by hand. */
final class ShromikaiPropertiesTestData {

  private ShromikaiPropertiesTestData() {}

  static ShromikaiProperties.Auth auth(String jwtSecret) {
    return new ShromikaiProperties.Auth(
        jwtSecret,
        "shromikai",
        Duration.ofMinutes(15),
        Duration.ofDays(7),
        Duration.ofHours(2),
        new ShromikaiProperties.Lockout(5, Duration.ofMinutes(15)),
        new ShromikaiProperties.RateLimit(20, 10),
        12,
        true,
        false,
        ShromikaiPropertiesTest.DEMO_PASSWORD);
  }

  static ShromikaiProperties.Quotas quotas() {
    return new ShromikaiProperties.Quotas(3, 10, 1, ZoneId.of("Asia/Dhaka"));
  }
}
