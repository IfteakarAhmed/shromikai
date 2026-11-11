package bd.shromikai.shared.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;

/**
 * Refuses to start an unsafe configuration: contract storage switched on (R-02) or the {@code prod}
 * profile active together with {@code demo} or {@code dev} (architecture §16.3).
 */
public final class StartupGuards {

  private StartupGuards() {}

  public static void check(Environment environment, ShromikaiProperties properties) {
    if (properties.privacy().storeContracts()) {
      throw new IllegalStateException(
          "shromikai.privacy.store-contracts must be false: contract text is never stored (R-02)");
    }
    if (properties.auth().jwtSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        < 32) {
      throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
    }
    List<String> active = Arrays.asList(environment.getActiveProfiles());
    if (active.contains("prod") && active.contains("demo")) {
      throw new IllegalStateException("The prod profile refuses to start together with demo");
    }
    if (active.contains("prod") && active.contains("dev")) {
      throw new IllegalStateException(
          "The prod profile refuses to start together with dev (it exposes the debug endpoints)");
    }
  }
}
