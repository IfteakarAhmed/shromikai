package bd.shromikai.auth;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
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

/** FR-65: keys are created and revoked by an ADMIN per organisation, shown once, stored hashed. */
@IntegrationTest
@Requirement("FR-65")
class ApiKeyAdminIT {

  @Autowired TestRestTemplate http;
  @Autowired JdbcClient jdbc;
  @Autowired JwtService jwt;
  @Autowired PasswordEncoder encoder;

  private UUID org;
  private String admin;

  @BeforeEach
  void organisationWithAdmin() {
    org =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values ('Key org', 'NGO', 'API')"
                    + " returning id")
            .query(UUID.class)
            .single();
    UUID user =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            IdentityFixtures.uniqueEmail("admin"),
            IdentityFixtures.PASSWORD,
            Role.ADMIN,
            false);
    jdbc.sql("update app_user set org_id = :o where id = :u")
        .param("o", org)
        .param("u", user)
        .update();
    admin = jwt.issueUser(user, Role.ADMIN, org, false).value();
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from api_key").update();
    jdbc.sql("delete from app_user where org_id = :o").param("o", org).update();
    jdbc.sql("delete from organisation where id = :o").param("o", org).update();
  }

  private ResponseEntity<JsonNode> send(HttpMethod method, String token, String path, Object body) {
    HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private JsonNode create(String name, Object scopes) {
    ResponseEntity<JsonNode> response =
        send(
            HttpMethod.POST,
            admin,
            "/api/v1/admin/api-keys",
            Map.of("name", name, "scopes", scopes));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  @Test
  void theKeyIsShownOnceAndOnlyItsSha256IsStored() {
    JsonNode created = create("Claude Desktop", java.util.List.of("mcp", "laws"));

    String key = created.path("key").asText();
    assertThat(key).matches("shk_[0-9A-Za-z]{40}");
    assertThat(created.path("prefix").asText()).isEqualTo(key.substring(0, 8));
    assertThat(created.path("scopes")).extracting(JsonNode::asText).containsExactly("mcp", "laws");
    String stored =
        jdbc.sql("select key_sha256 from api_key where id = cast(:id as uuid)")
            .param("id", created.path("id").asText())
            .query(String.class)
            .single();
    assertThat(stored).isEqualTo(Hashing.sha256Hex(key));

    ResponseEntity<JsonNode> listing = send(HttpMethod.GET, admin, "/api/v1/admin/api-keys", null);
    assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listing.getBody().toString()).doesNotContain(key).doesNotContain(stored);
    assertThat(listing.getBody()).hasSize(1);
    assertThat(listing.getBody().get(0).path("name").asText()).isEqualTo("Claude Desktop");
  }

  @Test
  void revokingSetsRevokedAtAndIsAudited() {
    JsonNode created = create("Temp", java.util.List.of("mcp"));
    String id = created.path("id").asText();

    ResponseEntity<JsonNode> revoked =
        send(HttpMethod.DELETE, admin, "/api/v1/admin/api-keys/" + id, null);

    assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            jdbc.sql("select revoked_at is not null from api_key where id = cast(:id as uuid)")
                .param("id", id)
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'API_KEY_REVOKED'"
                        + " and target_id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1L);
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'API_KEY_CREATED'"
                        + " and target_id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1L);
  }

  @Test
  void unknownScopesAndEmptyScopesAreRefused() {
    assertThat(
            send(
                    HttpMethod.POST,
                    admin,
                    "/api/v1/admin/api-keys",
                    Map.of("name", "x", "scopes", java.util.List.of("root")))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            send(
                    HttpMethod.POST,
                    admin,
                    "/api/v1/admin/api-keys",
                    Map.of("name", "x", "scopes", java.util.List.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aNameLongerThanSixtyCharactersIsRefused() {
    assertThat(
            send(
                    HttpMethod.POST,
                    admin,
                    "/api/v1/admin/api-keys",
                    Map.of("name", "n".repeat(61), "scopes", java.util.List.of("mcp")))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void onlyAnAdminMayManageKeys() {
    String hr = jwt.issueUser(UUID.randomUUID(), Role.HR, org, false).value();

    assertThat(send(HttpMethod.GET, hr, "/api/v1/admin/api-keys", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(send(HttpMethod.GET, null, "/api/v1/admin/api-keys", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
