package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.PASSWORD;
import static bd.shromikai.testsupport.IdentityFixtures.insertUser;
import static bd.shromikai.testsupport.IdentityFixtures.login;
import static bd.shromikai.testsupport.IdentityFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/** Production limits: 20 requests per minute per IP, 10 per minute per user (FR-07). */
/** Production limit: 10 requests per minute per user (FR-07). */
@IntegrationTest
@Requirement("FR-07")
@TestPropertySource(
    properties = {
      "shromikai.auth.rate-limit.per-ip-per-minute=100000",
      "shromikai.auth.rate-limit.per-user-per-minute=10"
    })
class RateLimitPerUserIT {

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  @Test
  void shouldReturn429_onTheEleventhLoginForOneEmail_butNotForAnother() {
    String email = uniqueEmail("target");
    String other = uniqueEmail("other");
    insertUser(jdbc, encoder, email, PASSWORD, Role.WORKER, false);
    insertUser(jdbc, encoder, other, PASSWORD, Role.WORKER, false);
    for (int attempt = 1; attempt <= 10; attempt++) {
      assertThat(login(http, email, PASSWORD).getStatusCode())
          .as("attempt %d", attempt)
          .isEqualTo(HttpStatus.OK);
    }

    assertThat(login(http, email, PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(login(http, other, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
