package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/** api.md §2 access matrix, parameterised over caller × endpoint group (FR-06). */
@IntegrationTest
@Requirement("FR-06")
class RoleAccessIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;

  private static final String ADMIN_ONLY = "/api/v1/admin/probe";
  private static final String PLAYBOOKS = "/api/v1/playbooks";
  private static final String REVIEW_ITEMS = "/api/v1/review-items";
  private static final String DEV = "/api/v1/dev/probe";
  private static final String ANY_SIGNED_IN = "/api/v1/laws";

  static Stream<Arguments> matrix() {
    // caller, path, expected status
    return Stream.of(
        Arguments.of("ANON", ADMIN_ONLY, 401),
        Arguments.of("GUEST", ADMIN_ONLY, 403),
        Arguments.of("WORKER", ADMIN_ONLY, 403),
        Arguments.of("HR", ADMIN_ONLY, 403),
        Arguments.of("ADMIN", ADMIN_ONLY, 200),
        Arguments.of("ANON", PLAYBOOKS, 401),
        Arguments.of("GUEST", PLAYBOOKS, 403),
        Arguments.of("WORKER", PLAYBOOKS, 403),
        Arguments.of("HR", PLAYBOOKS, 200),
        Arguments.of("ADMIN", PLAYBOOKS, 200),
        Arguments.of("ANON", REVIEW_ITEMS, 401),
        Arguments.of("GUEST", REVIEW_ITEMS, 403),
        Arguments.of("WORKER", REVIEW_ITEMS, 403),
        Arguments.of("HR", REVIEW_ITEMS, 403),
        Arguments.of("ADMIN", REVIEW_ITEMS, 200),
        Arguments.of("WORKER", DEV, 403),
        Arguments.of("HR", DEV, 403),
        Arguments.of("ADMIN", DEV, 200),
        Arguments.of("ANON", ANY_SIGNED_IN, 401),
        Arguments.of("GUEST", ANY_SIGNED_IN, 200),
        Arguments.of("WORKER", ANY_SIGNED_IN, 200),
        Arguments.of("HR", ANY_SIGNED_IN, 200),
        Arguments.of("ADMIN", ANY_SIGNED_IN, 200));
  }

  private HttpHeaders headersFor(String caller) {
    return switch (caller) {
      case "ANON" -> new HttpHeaders();
      case "GUEST" -> bearer(jwt.issueGuest("d".repeat(64)).value());
      default ->
          bearer(jwt.issueUser(UUID.randomUUID(), Role.valueOf(caller), null, false).value());
    };
  }

  @ParameterizedTest(name = "{0} GET {1} -> {2}")
  @MethodSource("matrix")
  void shouldFollowTheAccessMatrix(String caller, String path, int expected) {
    ResponseEntity<JsonNode> response =
        http.exchange(path, HttpMethod.GET, new HttpEntity<>(headersFor(caller)), JsonNode.class);

    assertThat(response.getStatusCode().value()).isEqualTo(expected);
    if (expected == 403) {
      assertThat(response.getBody().get("code").asText()).isEqualTo("forbidden");
      assertThat(response.getHeaders().getContentType().toString())
          .startsWith("application/problem+json");
    }
    if (expected == 401) {
      assertThat(response.getBody().get("code").asText()).isEqualTo("invalid-token");
    }
  }

  @Test
  void shouldKeepAdminOffLimitsToWorkers_forEveryMethodAndSubPath() {
    HttpHeaders worker = headersFor("WORKER");

    for (HttpMethod method :
        new HttpMethod[] {
          HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE
        }) {
      for (String path :
          new String[] {
            "/api/v1/admin/users", "/api/v1/admin/laws/ingest", "/api/v1/admin/anything/deeper"
          }) {
        assertThat(
                http.exchange(path, method, new HttpEntity<>(worker), JsonNode.class)
                    .getStatusCode()
                    .value())
            .as("%s %s", method, path)
            .isEqualTo(403);
      }
    }
  }

  @Test
  void shouldRequireAuthentication_forUnknownPaths_andAnswerNotFoundWhenSignedIn() {
    assertThat(http.getForEntity("/api/v1/does-not-exist", JsonNode.class).getStatusCode().value())
        .isEqualTo(401);
    assertThat(
            http.exchange(
                    "/api/v1/does-not-exist",
                    HttpMethod.GET,
                    new HttpEntity<>(headersFor("WORKER")),
                    JsonNode.class)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void shouldKeepPublicEndpointsPublic() {
    for (String path :
        new String[] {
          "/api/v1/meta", "/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"
        }) {
      assertThat(http.getForEntity(path, String.class).getStatusCode().value())
          .as(path)
          .isEqualTo(200);
    }
  }

  @Test
  void shouldKeepGuestsAwayFromAccountMutation() {
    HttpHeaders guest = headersFor("GUEST");

    assertThat(
            http.exchange(
                    "/api/v1/auth/me/password",
                    HttpMethod.POST,
                    new HttpEntity<>(guest),
                    JsonNode.class)
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            http.exchange(
                    "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(guest), JsonNode.class)
                .getStatusCode()
                .value())
        .isEqualTo(200);
  }
}
