package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.PASSWORD;
import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static bd.shromikai.testsupport.IdentityFixtures.insertUser;
import static bd.shromikai.testsupport.IdentityFixtures.login;
import static bd.shromikai.testsupport.IdentityFixtures.refreshCookie;
import static bd.shromikai.testsupport.IdentityFixtures.uniqueEmail;
import static bd.shromikai.testsupport.IdentityFixtures.withRefreshCookie;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@IntegrationTest
@Requirement("FR-05")
@Requirement("FR-07")
class AuthControllerIT {

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private String email;
  private UUID userId;

  @BeforeEach
  void createWorker() {
    email = uniqueEmail("worker");
    userId = insertUser(jdbc, encoder, email, PASSWORD, Role.WORKER, false);
  }

  @Test
  void shouldIssueTokens_whenCredentialsAreCorrect() {
    ResponseEntity<JsonNode> response = login(http, email, PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = response.getBody();
    assertThat(body.get("accessToken").asText()).isNotBlank();
    assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
    assertThat(body.get("expiresIn").asInt()).isEqualTo(900);
    assertThat(body.get("guest").asBoolean()).isFalse();
    assertThat(body.at("/user/email").asText()).isEqualTo(email);
    assertThat(body.at("/user/role").asText()).isEqualTo("WORKER");
    assertThat(body.at("/user/demo").asBoolean()).isFalse();
    assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(cookie)
        .contains("shromik_refresh=")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Path=/api/v1/auth");
  }

  @Test
  void shouldStoreOnlyTheHashOfTheRefreshToken_andABcryptPassword() {
    ResponseEntity<JsonNode> response = login(http, email, PASSWORD);
    String raw = refreshCookie(response);

    List<String> stored =
        jdbc.sql("select token_sha256 from refresh_token where user_id = :u")
            .param("u", userId)
            .query(String.class)
            .list();
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0)).hasSize(64).isNotEqualTo(raw);
    String hash =
        jdbc.sql("select password_hash from app_user where id = :u")
            .param("u", userId)
            .query(String.class)
            .single();
    assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
  }

  @Test
  void shouldReturn401Problem_whenPasswordIsWrong() {
    ResponseEntity<JsonNode> response = login(http, email, "wrong-password-123");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(response.getBody().get("code").asText()).isEqualTo("invalid-credentials");
    assertThat(refreshCookie(response)).isNull();
  }

  @Test
  void shouldAnswerTheSame_whenTheEmailIsUnknown() {
    ResponseEntity<JsonNode> response = login(http, uniqueEmail("nobody"), PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().get("code").asText()).isEqualTo("invalid-credentials");
  }

  @Test
  void shouldReturn400_whenBodyIsInvalid() {
    ResponseEntity<JsonNode> response = login(http, "not-an-email", "");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("code").asText()).isEqualTo("validation-failed");
  }

  @Test
  void shouldReturn423_onTheSixthAttempt_evenWithTheCorrectPassword() {
    for (int attempt = 1; attempt <= 5; attempt++) {
      assertThat(login(http, email, "wrong-password-123").getStatusCode())
          .as("attempt %d", attempt)
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    ResponseEntity<JsonNode> sixth = login(http, email, PASSWORD);

    assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.valueOf(423));
    assertThat(sixth.getBody().get("code").asText()).isEqualTo("account-locked");
    assertThat(sixth.getBody().get("retryAfterSeconds").asInt()).isBetween(1, 900);
    assertThat(sixth.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
  }

  @Test
  void shouldAcceptLogin_afterTheLockWindowEnds_andResetTheCounter() {
    for (int attempt = 1; attempt <= 5; attempt++) {
      login(http, email, "wrong-password-123");
    }
    jdbc.sql("update app_user set locked_until = now() - interval '1 minute' where id = :u")
        .param("u", userId)
        .update();

    assertThat(login(http, email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            jdbc.sql("select failed_logins from app_user where id = :u")
                .param("u", userId)
                .query(Integer.class)
                .single())
        .isZero();
  }

  @Test
  void shouldRefuseLogin_whenAccountIsDisabled() {
    jdbc.sql("update app_user set disabled_at = now() where id = :u").param("u", userId).update();

    assertThat(login(http, email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldRotateRefreshToken_andRejectTheOldOne() {
    String first = refreshCookie(login(http, email, PASSWORD));

    ResponseEntity<JsonNode> rotated =
        http.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            withRefreshCookie(first, null),
            JsonNode.class);

    assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
    String second = refreshCookie(rotated);
    assertThat(second).isNotBlank().isNotEqualTo(first);
    assertThat(rotated.getBody().get("accessToken").asText()).isNotBlank();

    ResponseEntity<JsonNode> replay =
        http.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            withRefreshCookie(first, null),
            JsonNode.class);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(replay.getBody().get("code").asText()).isEqualTo("invalid-token");
  }

  @Test
  void shouldRevokeTheWholeFamily_whenARotatedTokenIsReused() {
    String first = refreshCookie(login(http, email, PASSWORD));
    String second =
        refreshCookie(
            http.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                withRefreshCookie(first, null),
                JsonNode.class));

    http.exchange(
        "/api/v1/auth/refresh", HttpMethod.POST, withRefreshCookie(first, null), JsonNode.class);
    ResponseEntity<JsonNode> latest =
        http.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            withRefreshCookie(second, null),
            JsonNode.class);

    assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldRejectRefresh_whenThereIsNoCookie_orItIsUnknown() {
    assertThat(http.postForEntity("/api/v1/auth/refresh", null, JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            http.exchange(
                    "/api/v1/auth/refresh",
                    HttpMethod.POST,
                    withRefreshCookie("unknown-token-value", null),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldRejectRefresh_whenTheTokenIsExpired() {
    String cookie = refreshCookie(login(http, email, PASSWORD));
    jdbc.sql("update refresh_token set expires_at = now() - interval '1 second' where user_id = :u")
        .param("u", userId)
        .update();

    assertThat(
            http.exchange(
                    "/api/v1/auth/refresh",
                    HttpMethod.POST,
                    withRefreshCookie(cookie, null),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldRevokeRefreshToken_whenLoggingOut() {
    ResponseEntity<JsonNode> session = login(http, email, PASSWORD);
    String cookie = refreshCookie(session);
    String access = session.getBody().get("accessToken").asText();

    ResponseEntity<Void> logout =
        http.exchange(
            "/api/v1/auth/logout", HttpMethod.POST, withRefreshCookie(cookie, access), Void.class);

    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    ResponseEntity<JsonNode> refresh =
        http.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            withRefreshCookie(cookie, null),
            JsonNode.class);
    assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldNotRevokeAnotherUsersToken_whenLoggingOutWithTheirCookie() {
    String other = uniqueEmail("other");
    insertUser(jdbc, encoder, other, PASSWORD, Role.WORKER, false);
    String othersCookie = refreshCookie(login(http, other, PASSWORD));
    String myAccess = login(http, email, PASSWORD).getBody().get("accessToken").asText();

    http.exchange(
        "/api/v1/auth/logout",
        HttpMethod.POST,
        withRefreshCookie(othersCookie, myAccess),
        Void.class);

    assertThat(
            http.exchange(
                    "/api/v1/auth/refresh",
                    HttpMethod.POST,
                    withRefreshCookie(othersCookie, null),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldRequireAuthentication_forLogoutAndMe() {
    assertThat(http.postForEntity("/api/v1/auth/logout", null, JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    ResponseEntity<JsonNode> me = http.getForEntity("/api/v1/auth/me", JsonNode.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me.getBody().get("code").asText()).isEqualTo("invalid-token");
  }

  @Test
  void shouldReturnProfile_whenCallingMe() {
    String access = login(http, email, PASSWORD).getBody().get("accessToken").asText();

    ResponseEntity<JsonNode> me =
        http.exchange(
            "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(access)), JsonNode.class);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().get("userId").asText()).isEqualTo(userId.toString());
    assertThat(me.getBody().get("email").asText()).isEqualTo(email);
    assertThat(me.getBody().get("role").asText()).isEqualTo("WORKER");
    assertThat(me.getBody().get("locale").asText()).isEqualTo("bn");
    assertThat(me.getBody().get("org").isNull()).isTrue();
    assertThat(me.getBody().get("demo").asBoolean()).isFalse();
    assertThat(me.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void shouldRejectCredentialsInTheQueryString_beforeAuthentication() {
    String access = login(http, email, PASSWORD).getBody().get("accessToken").asText();

    for (String param : List.of("access_token", "token", "api_key")) {
      ResponseEntity<JsonNode> response =
          http.getForEntity("/api/v1/auth/me?" + param + "=" + access, JsonNode.class);
      assertThat(response.getStatusCode()).as(param).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().get("code").asText()).isEqualTo("validation-failed");
      assertThat(response.getBody().toString()).doesNotContain(access);
    }
  }

  @Test
  void shouldRejectAnExpiredOrForgedBearerToken() {
    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/auth/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer("eyJhbGciOiJIUzI1NiJ9.e30.forged")),
            JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldChangePassword_andRevokeRefreshTokens() {
    ResponseEntity<JsonNode> session = login(http, email, PASSWORD);
    String access = session.getBody().get("accessToken").asText();
    String cookie = refreshCookie(session);
    String newPassword = "a-brand-new-passphrase-2";

    ResponseEntity<Void> changed =
        http.exchange(
            "/api/v1/auth/me/password",
            HttpMethod.POST,
            jsonWithBearer(Map.of("currentPassword", PASSWORD, "newPassword", newPassword), access),
            Void.class);

    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(login(http, email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(login(http, email, newPassword).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            http.exchange(
                    "/api/v1/auth/refresh",
                    HttpMethod.POST,
                    withRefreshCookie(cookie, null),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldRefusePasswordChange_whenCurrentPasswordIsWrongOrNewOneIsShort() {
    String access = login(http, email, PASSWORD).getBody().get("accessToken").asText();

    ResponseEntity<JsonNode> wrong =
        http.exchange(
            "/api/v1/auth/me/password",
            HttpMethod.POST,
            jsonWithBearer(
                Map.of("currentPassword", "nope-nope-nope-1", "newPassword", "another-long-one-3"),
                access),
            JsonNode.class);
    ResponseEntity<JsonNode> shortOne =
        http.exchange(
            "/api/v1/auth/me/password",
            HttpMethod.POST,
            jsonWithBearer(Map.of("currentPassword", PASSWORD, "newPassword", "short"), access),
            JsonNode.class);

    assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(shortOne.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  static HttpEntity<Map<String, String>> jsonWithBearer(Map<String, String> body, String access) {
    HttpHeaders headers = bearer(access);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }
}
