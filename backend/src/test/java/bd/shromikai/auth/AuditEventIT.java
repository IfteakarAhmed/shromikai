package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.PASSWORD;
import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static bd.shromikai.testsupport.IdentityFixtures.insertUser;
import static bd.shromikai.testsupport.IdentityFixtures.login;
import static bd.shromikai.testsupport.IdentityFixtures.refreshCookie;
import static bd.shromikai.testsupport.IdentityFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** audit_event rows for login, demo login, denied access and admin actions (FR-10). */
@IntegrationTest
@Requirement("FR-10")
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "shromikai.auth.demo-password=demo-password-for-tests-1")
class AuditEventIT {

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtService jwt;

  private long count(String action) {
    return jdbc.sql("select count(*) from audit_event where action = :a")
        .param("a", action)
        .query(Long.class)
        .single();
  }

  private List<Map<String, Object>> rowsFor(String action, UUID actor) {
    return jdbc.sql(
            "select actor_kind, actor_id, target_type, target_id, detail::text as detail from audit_event"
                + " where action = :a and actor_id = :u")
        .param("a", action)
        .param("u", actor)
        .query()
        .listOfRows();
  }

  private static void assertNoSecrets(Object row, String... secrets) {
    for (String secret : secrets) {
      assertThat(row.toString()).doesNotContain(secret);
    }
  }

  @Test
  void shouldWriteExactlyOneRow_onLoginSuccess() {
    String email = uniqueEmail("worker");
    UUID id = insertUser(jdbc, encoder, email, PASSWORD, Role.WORKER, false);
    long before = count("LOGIN_SUCCESS");

    ResponseEntity<JsonNode> response = login(http, email, PASSWORD);

    assertThat(count("LOGIN_SUCCESS")).isEqualTo(before + 1);
    List<Map<String, Object>> rows = rowsFor("LOGIN_SUCCESS", id);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("actor_kind", "USER");
    assertNoSecrets(
        rows,
        PASSWORD,
        email,
        response.getBody().get("accessToken").asText(),
        refreshCookie(response));
  }

  @Test
  void shouldWriteExactlyOneRow_onLoginFailure_forAKnownUser() {
    String email = uniqueEmail("worker");
    UUID id = insertUser(jdbc, encoder, email, PASSWORD, Role.WORKER, false);
    long before = count("LOGIN_FAILURE");

    login(http, email, "wrong-password-123");

    assertThat(count("LOGIN_FAILURE")).isEqualTo(before + 1);
    List<Map<String, Object>> rows = rowsFor("LOGIN_FAILURE", id);
    assertThat(rows).hasSize(1);
    assertNoSecrets(rows, "wrong-password-123", email);
  }

  @Test
  void shouldWriteExactlyOneRow_onLoginFailure_forAnUnknownEmail_withoutStoringIt() {
    String email = uniqueEmail("nobody");
    long before = count("LOGIN_FAILURE");

    login(http, email, "wrong-password-123");

    assertThat(count("LOGIN_FAILURE")).isEqualTo(before + 1);
    List<Map<String, Object>> latest =
        jdbc.sql(
                "select actor_id, detail::text as detail, target_id from audit_event"
                    + " where action = 'LOGIN_FAILURE' order by occurred_at desc limit 1")
            .query()
            .listOfRows();
    assertThat(latest.get(0).get("actor_id")).isNull();
    assertNoSecrets(latest, email, "wrong-password-123", "nobody");
  }

  @Test
  void shouldWriteExactlyOneRow_onDemoLogin() {
    long before = count("DEMO_LOGIN");

    ResponseEntity<JsonNode> response =
        http.postForEntity("/api/v1/auth/demo/hr", null, JsonNode.class);

    assertThat(count("DEMO_LOGIN")).isEqualTo(before + 1);
    UUID demoUser = UUID.fromString(response.getBody().at("/user/userId").asText());
    List<Map<String, Object>> rows = rowsFor("DEMO_LOGIN", demoUser);
    assertThat(rows).hasSize(1);
    assertNoSecrets(rows, response.getBody().get("accessToken").asText(), "demo-password");
  }

  @Test
  void shouldWriteOneAccessDeniedRow_whenARoleIsNotAllowed() {
    UUID worker = UUID.randomUUID();
    String token = jwt.issueUser(worker, Role.WORKER, null, false).value();
    long before = count("ACCESS_DENIED");

    http.exchange(
        "/api/v1/admin/probe", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);

    assertThat(count("ACCESS_DENIED")).isEqualTo(before + 1);
    List<Map<String, Object>> rows = rowsFor("ACCESS_DENIED", worker);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("detail").toString()).contains("/api/v1/admin/probe");
    assertNoSecrets(rows, token);
  }

  @Test
  void shouldWriteAnAdminActionRow_forAnAdminWriteButNotForARead() {
    UUID admin = UUID.randomUUID();
    String token = jwt.issueUser(admin, Role.ADMIN, null, false).value();

    http.exchange(
        "/api/v1/admin/probe", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
    assertThat(rowsFor("ADMIN_ACTION", admin)).isEmpty();

    http.exchange(
        "/api/v1/admin/probe", HttpMethod.POST, new HttpEntity<>(bearer(token)), JsonNode.class);
    assertThat(rowsFor("ADMIN_ACTION", admin)).hasSize(1);
  }

  @Test
  void shouldRefuseToUpdateOrTruncateAuditRows() {
    String email = uniqueEmail("worker");
    insertUser(jdbc, encoder, email, PASSWORD, Role.WORKER, false);
    login(http, email, PASSWORD);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.sql("update audit_event set target_id = 'x'").update())
        .hasMessageContaining("append-only");
  }
}
