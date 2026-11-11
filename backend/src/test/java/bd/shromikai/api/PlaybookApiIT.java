package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * FR-57, NFR-12, T-26: playbook CRUD with validation pointers, the limit of 10 active playbooks,
 * and isolation between organisations (a stranger gets 404 on read, update, archive and list).
 */
@IntegrationTest
@Requirement("FR-57")
@Requirement("NFR-12")
class PlaybookApiIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private UUID orgA;
  private UUID orgB;
  private String hrA;
  private String hrB;
  private String admin;

  @BeforeEach
  void organisations() {
    orgA = org("Org A");
    orgB = org("Org B");
    hrA = hr(orgA);
    hrB = hr(orgB);
    admin = jwt.issueUser(UUID.randomUUID(), Role.ADMIN, null, false).value();
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from playbook").update();
    jdbc.sql("delete from app_user where org_id in (:a, :b)")
        .param("a", orgA)
        .param("b", orgB)
        .update();
    jdbc.sql("delete from organisation where id in (:a, :b)")
        .param("a", orgA)
        .param("b", orgB)
        .update();
  }

  private UUID org(String name) {
    return jdbc.sql(
            "insert into organisation (name, kind, plan) values (:n, 'HR', 'TEAM') returning id")
        .param("n", name)
        .query(UUID.class)
        .single();
  }

  private String hr(UUID org) {
    UUID user =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            IdentityFixtures.uniqueEmail("hr"),
            IdentityFixtures.PASSWORD,
            Role.HR,
            false);
    jdbc.sql("update app_user set org_id = :org where id = :id")
        .param("org", org)
        .param("id", user)
        .update();
    return jwt.issueUser(user, Role.HR, org, false).value();
  }

  private static String playbook(String name, String unit) {
    return """
        {"name":"%s","side":"EMPLOYER","rules":[{"clauseType":"PROBATION",
         "fact":"PROBATION_LENGTH","comparator":"MAX","value":3,"unit":"%s","severity":"MEDIUM",
         "messageBn":"আমাদের নীতিতে শিক্ষানবিশকাল সর্বোচ্চ ৩ মাস।",
         "messageEn":"Our standard probation is 3 months.","statuteSectionKey":null}]}
        """
        .formatted(name, unit);
  }

  private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, String body) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private String create(String token, String name) {
    ResponseEntity<JsonNode> created =
        call(HttpMethod.POST, "/api/v1/playbooks", token, playbook(name, "MONTHS"));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asText();
  }

  @Test
  void anHrUserCreatesReadsUpdatesAndArchivesAPlaybookOfTheOwnOrganisation() {
    String id = create(hrA, "Standard staff contract");

    ResponseEntity<JsonNode> read = call(HttpMethod.GET, "/api/v1/playbooks/" + id, hrA, null);
    assertThat(read.getBody().path("rules").get(0).path("messageEn").asText())
        .isEqualTo("Our standard probation is 3 months.");
    assertThat(read.getBody().path("version").asInt()).isEqualTo(1);
    assertThat(read.getBody().path("orgId").asText()).isEqualTo(orgA.toString());

    ResponseEntity<JsonNode> updated =
        call(HttpMethod.PUT, "/api/v1/playbooks/" + id, hrA, playbook("Renamed", "MONTHS"));
    assertThat(updated.getBody().path("version").asInt()).isEqualTo(2);
    assertThat(updated.getBody().path("name").asText()).isEqualTo("Renamed");

    assertThat(call(HttpMethod.DELETE, "/api/v1/playbooks/" + id, hrA, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(call(HttpMethod.GET, "/api/v1/playbooks/" + id, hrA, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anIncompatibleUnitIsA400WithThePointerOfTheUnit() {
    ResponseEntity<JsonNode> response =
        call(HttpMethod.POST, "/api/v1/playbooks", hrA, playbook("Bad", "HOURS_PER_DAY"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("validation-failed");
    assertThat(response.getBody().path("errors").get(0).path("pointer").asText())
        .isEqualTo("/rules/0/unit");
  }

  @Test
  void theEleventhActivePlaybookIsRefusedAndArchivingFreesASlot() {
    String first = create(hrA, "P0");
    for (int i = 1; i < 10; i++) {
      create(hrA, "P" + i);
    }

    ResponseEntity<JsonNode> eleventh =
        call(HttpMethod.POST, "/api/v1/playbooks", hrA, playbook("P10", "MONTHS"));
    assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(eleventh.getBody().path("code").asText()).isEqualTo("playbook-limit-reached");

    call(HttpMethod.DELETE, "/api/v1/playbooks/" + first, hrA, null);
    assertThat(create(hrA, "P10")).isNotBlank();
  }

  @Test
  void anotherOrganisationGets404OnReadUpdateArchiveAndSeesNothingInTheList() {
    String id = create(hrA, "Private to A");

    assertThat(call(HttpMethod.GET, "/api/v1/playbooks/" + id, hrB, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(HttpMethod.PUT, "/api/v1/playbooks/" + id, hrB, playbook("Taken", "MONTHS"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.DELETE, "/api/v1/playbooks/" + id, hrB, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    ResponseEntity<JsonNode> list = call(HttpMethod.GET, "/api/v1/playbooks", hrB, null);
    assertThat(list.getBody().path("items")).isEmpty();
    assertThat(list.getBody().path("totalItems").asLong()).isZero();
    // the owner still has it, and an admin can see it
    assertThat(call(HttpMethod.GET, "/api/v1/playbooks/" + id, hrA, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(call(HttpMethod.GET, "/api/v1/playbooks/" + id, admin, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void aWorkerCannotUsePlaybooks() {
    String worker = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();

    assertThat(call(HttpMethod.GET, "/api/v1/playbooks", worker, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
