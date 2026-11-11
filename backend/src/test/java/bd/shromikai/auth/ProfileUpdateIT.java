package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.PASSWORD;
import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static bd.shromikai.testsupport.IdentityFixtures.insertUser;
import static bd.shromikai.testsupport.IdentityFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * FR-56 (E-09): {@code PATCH /api/v1/auth/me} stores the locale and the personalisation profile of
 * an account; a guest cannot (403); values are validated; omitted fields stay as they were.
 */
@IntegrationTest
@Requirement("FR-56")
class ProfileUpdateIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private UUID userId;
  private String token;

  @BeforeEach
  void aWorker() {
    userId = insertUser(jdbc, encoder, uniqueEmail("profile"), PASSWORD, Role.WORKER, false);
    token = jwt.issueUser(userId, Role.WORKER, null, false).value();
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from app_user where id = :id").param("id", userId).update();
  }

  private ResponseEntity<JsonNode> patch(String access, String body) {
    HttpHeaders headers = bearer(access);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/auth/me", HttpMethod.PATCH, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private ResponseEntity<JsonNode> me(String access) {
    return http.exchange(
        "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(access)), JsonNode.class);
  }

  private static final String PROFILE =
      "{\"side\":\"WORKER\",\"sector\":\"READY_MADE_GARMENTS\",\"jobRole\":\"Sewing operator\","
          + "\"preferredLanguage\":\"bn\",\"readingLevel\":\"BASIC\"}";

  @Test
  void theProfileAndLocaleAreStoredAndReturnedByMe() {
    ResponseEntity<JsonNode> response =
        patch(token, "{\"locale\":\"en\",\"profile\":" + PROFILE + "}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("locale").asText()).isEqualTo("en");
    assertThat(response.getBody().path("profile").path("readingLevel").asText()).isEqualTo("BASIC");
    JsonNode again = me(token).getBody();
    assertThat(again.path("locale").asText()).isEqualTo("en");
    assertThat(again.path("profile").path("sector").asText()).isEqualTo("READY_MADE_GARMENTS");
    assertThat(again.path("profile").path("jobRole").asText()).isEqualTo("Sewing operator");
  }

  @Test
  void anOmittedFieldStaysAsItWas() {
    patch(token, "{\"locale\":\"en\",\"profile\":" + PROFILE + "}");

    ResponseEntity<JsonNode> response = patch(token, "{\"locale\":\"bn\"}");

    assertThat(response.getBody().path("locale").asText()).isEqualTo("bn");
    assertThat(response.getBody().path("profile").path("sector").asText())
        .isEqualTo("READY_MADE_GARMENTS");
  }

  @Test
  void aGuestCannotUpdateAProfile() {
    String guest = jwt.issueGuest(Hashing.sha256Hex("profile-device")).value();

    assertThat(patch(guest, "{\"locale\":\"en\"}").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void badValuesAre400WithAPointerAndNothingIsSaved() {
    ResponseEntity<JsonNode> badLocale = patch(token, "{\"locale\":\"fr\"}");
    ResponseEntity<JsonNode> badSector =
        patch(token, "{\"profile\":{\"sector\":\"MINING\",\"readingLevel\":\"BASIC\"}}");
    ResponseEntity<JsonNode> longRole =
        patch(token, "{\"profile\":{\"jobRole\":\"" + "x".repeat(61) + "\"}}");
    ResponseEntity<JsonNode> badLevel = patch(token, "{\"profile\":{\"readingLevel\":\"EXPERT\"}}");

    assertThat(badLocale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badSector.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badSector.getBody().path("errors").get(0).path("pointer").asText())
        .isEqualTo("/profile/sector");
    assertThat(longRole.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badLevel.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(me(token).getBody().path("locale").asText()).isEqualTo("bn");
    assertThat(me(token).getBody().path("profile").isNull()).isTrue();
  }

  @Test
  void anUnauthenticatedCallIs401() {
    assertThat(
            http.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.PATCH,
                    new HttpEntity<>("{}", new HttpHeaders()),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "{\"profile\":{\"side\":null}}",
        "{\"profile\":{\"preferredLanguage\":null}}",
        "{\"profile\":{\"readingLevel\":null}}",
        "{\"profile\":{\"sector\":null}}",
        "{\"locale\":\"en\",\"profile\":{\"side\":null}}",
        "null"
      })
  void aNullValueOrBodyIs400NotAServerError(String body) {
    ResponseEntity<JsonNode> response = patch(token, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
