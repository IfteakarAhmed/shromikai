package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-33: outside the {@code dev} profile the debug endpoint does not exist. */
@IntegrationTest
@Requirement("FR-33")
class DevEndpointIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;

  @Test
  void shouldReturn404ForTheDebugEndpointOutsideTheDevProfile() {
    String admin = jwt.issueUser(UUID.randomUUID(), Role.ADMIN, null, false).value();

    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/dev/analyses/{id}/redacted",
            HttpMethod.GET,
            new HttpEntity<>(bearer(admin)),
            JsonNode.class,
            UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(response.getBody().get("code").asText()).isEqualTo("not-found");
  }

  @Test
  void shouldReturn401ForAnAnonymousCaller() {
    ResponseEntity<JsonNode> response =
        http.getForEntity("/api/v1/dev/analyses/{id}/redacted", JsonNode.class, UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
