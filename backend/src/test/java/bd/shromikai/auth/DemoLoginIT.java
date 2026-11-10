package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static bd.shromikai.testsupport.IdentityFixtures.login;
import static bd.shromikai.testsupport.IdentityFixtures.refreshCookie;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** The demo profile is active: seeded accounts and one-click login (FR-08). */
@IntegrationTest
@Requirement("FR-08")
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "shromikai.auth.demo-password=demo-password-for-tests-1")
class DemoLoginIT {

  static final String DEMO_PASSWORD = "demo-password-for-tests-1";

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;

  private ResponseEntity<JsonNode> demo(String role) {
    return http.postForEntity("/api/v1/auth/demo/" + role, null, JsonNode.class);
  }

  @ParameterizedTest
  @CsvSource({"worker,WORKER", "hr,HR", "admin,ADMIN"})
  void shouldIssueTokensForTheSeededAccount(String path, String role) {
    ResponseEntity<JsonNode> response = demo(path);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = response.getBody();
    assertThat(body.at("/user/email").asText()).isEqualTo(path + "@demo.shromikai.app");
    assertThat(body.at("/user/role").asText()).isEqualTo(role);
    assertThat(body.at("/user/demo").asBoolean()).isTrue();
    assertThat(body.get("guest").asBoolean()).isFalse();
    assertThat(refreshCookie(response)).isNotBlank();
  }

  @Test
  void shouldGiveHrAndAdminAnOrganisation_andTheWorkerNone() {
    assertThat(demo("hr").getBody().at("/user/org/name").asText()).isNotBlank();
    assertThat(demo("admin").getBody().at("/user/org/id").asText()).isNotBlank();
    assertThat(demo("worker").getBody().at("/user/org").isNull()).isTrue();
  }

  @Test
  void shouldReturn404_forAnUnknownRole() {
    assertThat(demo("superuser").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldLetSeededAccountsLogInWithTheDemoPassword_andNoOther() {
    assertThat(login(http, "worker@demo.shromikai.app", DEMO_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(login(http, "worker@demo.shromikai.app", "not-the-demo-password").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldGiveTheAdminDemoTokenAdminAccess_andTheWorkerDemoTokenNone() {
    String admin = demo("admin").getBody().get("accessToken").asText();
    String worker = demo("worker").getBody().get("accessToken").asText();

    assertThat(get("/api/v1/admin/probe", admin)).isEqualTo(HttpStatus.OK);
    assertThat(get("/api/v1/admin/probe", worker)).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldRefuseDemoUserPasswordChange_with403DemoAccountRestricted() {
    String access = demo("worker").getBody().get("accessToken").asText();
    HttpHeaders headers = bearer(access);
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/auth/me/password",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("currentPassword", DEMO_PASSWORD, "newPassword", "a-different-passphrase-9"),
                headers),
            JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().get("code").asText()).isEqualTo("demo-account-restricted");
    assertThat(login(http, "worker@demo.shromikai.app", DEMO_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldKeepDemoLoginWorking_afterTheAccountWasLockedByBadPasswords() {
    for (int i = 0; i < 5; i++) {
      login(http, "hr@demo.shromikai.app", "wrong-password-123");
    }

    assertThat(demo("hr").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldSeedExactlyOneRowPerAccount_evenWhenStartedAgain() {
    assertThat(
            jdbc.sql("select count(*) from app_user where email like '%@demo.shromikai.app'")
                .query(Integer.class)
                .single())
        .isEqualTo(3);
  }

  private HttpStatus get(String path, String access) {
    return HttpStatus.valueOf(
        http.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(access)), JsonNode.class)
            .getStatusCode()
            .value());
  }
}
