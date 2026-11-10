package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.PASSWORD;
import static bd.shromikai.testsupport.IdentityFixtures.login;
import static bd.shromikai.testsupport.IdentityFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** Production limit: 20 requests per minute per IP (FR-07). */
@IntegrationTest
@Requirement("FR-07")
@TestPropertySource(
    properties = {
      "shromikai.auth.rate-limit.per-ip-per-minute=20",
      "shromikai.auth.rate-limit.per-user-per-minute=100000"
    })
class RateLimitIT {

  @Autowired TestRestTemplate http;

  @Test
  void shouldReturn429_onTheTwentyFirstLoginFromOneIp() {
    for (int attempt = 1; attempt <= 20; attempt++) {
      // A different email each time, so only the per-IP limit can trip.
      ResponseEntity<JsonNode> response = login(http, uniqueEmail("nobody"), PASSWORD);
      assertThat(response.getStatusCode())
          .as("attempt %d", attempt)
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    ResponseEntity<JsonNode> limited = login(http, uniqueEmail("nobody"), PASSWORD);

    assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(limited.getBody().get("code").asText()).isEqualTo("rate-limited");
    assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
    assertThat(limited.getBody().get("retryAfterSeconds").asInt()).isBetween(1, 60);
  }
}
