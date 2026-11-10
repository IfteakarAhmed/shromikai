package bd.shromikai.auth.internal;

import bd.shromikai.auth.Role;
import bd.shromikai.shared.config.ShromikaiProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the judge accounts at start-up, only in the {@code demo} profile and never together with
 * {@code prod} (FR-08). The password comes from {@code DEMO_PASSWORD}; without a password of at
 * least 12 characters the application refuses to start.
 */
@Component
@Profile("demo & !prod")
class DemoAccountSeeder implements ApplicationRunner {

  static final String DEMO_ORGANISATION = "ShromikAI Demo";
  public static final String DEMO_DOMAIN = "demo.shromikai.app";

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final ShromikaiProperties properties;

  DemoAccountSeeder(UserRepository users, PasswordEncoder encoder, ShromikaiProperties properties) {
    this.users = users;
    this.encoder = encoder;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.auth().demoPasswordConfigured()) {
      throw new IllegalStateException(
          "The demo profile needs DEMO_PASSWORD with at least 12 characters");
    }
    String hash = encoder.encode(properties.auth().demoPassword());
    var org = users.demoOrganisation(DEMO_ORGANISATION);
    users.upsertDemo("worker@" + DEMO_DOMAIN, hash, Role.WORKER, null);
    users.upsertDemo("hr@" + DEMO_DOMAIN, hash, Role.HR, org);
    users.upsertDemo("admin@" + DEMO_DOMAIN, hash, Role.ADMIN, org);
  }
}
