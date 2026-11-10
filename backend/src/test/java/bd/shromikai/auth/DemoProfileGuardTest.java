package bd.shromikai.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.config.StartupGuards;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

@Tag("unit")
@Requirement("FR-08")
class DemoProfileGuardTest {

  private static ShromikaiProperties properties(String jwtSecret) {
    java.util.Map<String, String> map = new java.util.HashMap<>();
    map.put("shromikai.models.reasoning", "r");
    map.put("shromikai.models.fast", "f");
    map.put("shromikai.models.verifier-fallback", "v");
    map.put("shromikai.models.local-pii", "l");
    map.put("shromikai.models.embedding", "e");
    map.put("shromikai.privacy.store-contracts", "false");
    map.put("shromikai.privacy.redact-money", "false");
    map.put("shromikai.cors.allowed-origins", "http://localhost:5173");
    map.put("shromikai.ollama.base-url", "http://localhost:11434");
    map.put("shromikai.ollama.health-timeout", "2s");
    map.put("shromikai.providers.anthropic.base-url", "https://a.invalid");
    map.put("shromikai.providers.gemini.base-url", "https://g.invalid");
    map.put("shromikai.fake-models.fixtures", "classpath:x");
    map.put("shromikai.fake-models.default-case", "default");
    map.put("shromikai.auth.jwt-secret", jwtSecret);
    map.put("shromikai.auth.issuer", "shromikai");
    map.put("shromikai.auth.access-token-ttl", "15m");
    map.put("shromikai.auth.refresh-token-ttl", "7d");
    map.put("shromikai.auth.guest-token-ttl", "2h");
    map.put("shromikai.auth.lockout.max-failures", "5");
    map.put("shromikai.auth.lockout.duration", "15m");
    map.put("shromikai.auth.rate-limit.per-ip-per-minute", "20");
    map.put("shromikai.auth.rate-limit.per-user-per-minute", "10");
    map.put("shromikai.auth.bcrypt-strength", "12");
    map.put("shromikai.auth.cookie-secure", "true");
    map.put("shromikai.auth.hsts", "false");
    map.put("shromikai.quotas.guest-per-device-per-day", "3");
    map.put("shromikai.quotas.guest-per-ip-per-day", "10");
    map.put("shromikai.quotas.guest-concurrency", "1");
    map.put("shromikai.quotas.timezone", "Asia/Dhaka");
    return new Binder(new MapConfigurationPropertySource(map))
        .bind("shromikai", ShromikaiProperties.class)
        .get();
  }

  @Test
  void shouldRefuseToStart_whenDemoAndProdAreBothActive() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("prod", "demo");

    assertThatThrownBy(() -> StartupGuards.check(env, properties("s".repeat(40))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("demo");
  }

  @Test
  void shouldRefuseToStart_whenJwtSecretIsShorterThan32Bytes() {
    MockEnvironment env = new MockEnvironment();

    assertThatThrownBy(() -> StartupGuards.check(env, properties("short-secret")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void shouldDefaultToBcryptStrength12_andMaskSecretsInToString() {
    ShromikaiProperties properties = properties("a-very-secret-value-that-is-long-enough-1234");

    org.assertj.core.api.Assertions.assertThat(properties.auth().bcryptStrength()).isEqualTo(12);
    org.assertj.core.api.Assertions.assertThat(properties.toString())
        .doesNotContain("a-very-secret-value");
  }
}
