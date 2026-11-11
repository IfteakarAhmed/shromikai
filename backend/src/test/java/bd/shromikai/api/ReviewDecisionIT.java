package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IdentityFixtures;
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
 * FR-60: only an ADMIN can decide, an item must be claimed first, a citation must pass the
 * existence and quote checks, and every decision is audited.
 */
@IntegrationTest
@Requirement("FR-60")
class ReviewDecisionIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private UUID analysis;
  private UUID item;
  private UUID adminId;
  private String admin;
  private String otherAdmin;

  @BeforeEach
  void anOpenItem() {
    FictionalCorpus.load(jdbc);
    adminId = user("admin", Role.ADMIN);
    admin = jwt.issueUser(adminId, Role.ADMIN, null, false).value();
    otherAdmin = jwt.issueUser(user("admin2", Role.ADMIN), Role.ADMIN, null, false).value();
    analysis = UUID.randomUUID();
    jdbc.sql(
            """
            insert into analysis_run (id, subject_kind, subject_hash, client_kind, side,
                                      output_language, input_kind, page_count, status,
                                      pipeline_version)
            values (:id, 'USER', repeat('a', 64), 'API', 'WORKER', 'en', 'TEXT', 1, 'SUCCEEDED', 'p7-1')
            """)
        .param("id", analysis)
        .update();
    item = UUID.randomUUID();
    jdbc.sql(
            """
            insert into review_item (id, kind, analysis_id, finding_id, clause_type, finding_kind,
                                     severity, side, risk_summary, facts, reason_codes, status,
                                     expires_at)
            values (:id, 'FINDING', :analysis, 'f1', 'TERMINATION', 'CONFLICT', 'HIGH', 'WORKER',
                    'Short notice of dismissal.', '[]'::jsonb, array['NOT_VERIFIED'], 'OPEN',
                    now() + interval '30 days')
            """)
        .param("id", item)
        .param("analysis", analysis)
        .update();
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from analysis_run").update();
    jdbc.sql("delete from app_user where email like 'review-%'").update();
    FictionalCorpus.clean(jdbc);
  }

  private UUID user(String prefix, Role role) {
    return IdentityFixtures.insertUser(
        jdbc,
        encoder,
        "review-" + prefix + "-" + UUID.randomUUID() + "@example.test",
        IdentityFixtures.PASSWORD,
        role,
        false);
  }

  private ResponseEntity<JsonNode> patch(String token, String body) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/review-items/" + item,
        HttpMethod.PATCH,
        new HttpEntity<>(body, headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String token, String path) {
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
  }

  private static String approve(String key, String quote) {
    return "{\"decision\":\"APPROVED_WITH_CITATION\",\"sectionKey\":\""
        + key
        + "\",\"quote\":\""
        + quote
        + "\",\"claim\":\"The section requires fourteen days of notice.\",\"note\":\"checked\"}";
  }

  @Test
  void onlyAnAdminCanListOrDecide() {
    String hr = jwt.issueUser(UUID.randomUUID(), Role.HR, null, false).value();
    String worker = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();

    assertThat(get(hr, "/api/v1/review-items").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patch(worker, "{\"status\":\"IN_REVIEW\"}").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patch(hr, "{\"status\":\"IN_REVIEW\"}").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get(admin, "/api/v1/review-items").getBody().path("totalItems").asLong())
        .isEqualTo(1);
    assertThat(get(admin, "/api/v1/review-items/" + item).getBody().path("status").asText())
        .isEqualTo("OPEN");
  }

  @Test
  void anItemMustBeClaimedBeforeItIsDecidedAndAClaimIsExclusive() {
    ResponseEntity<JsonNode> early = patch(admin, "{\"decision\":\"REJECTED\",\"note\":\"no\"}");
    assertThat(early.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(early.getBody().path("code").asText()).isEqualTo("invalid-transition");

    assertThat(patch(admin, "{\"status\":\"IN_REVIEW\"}").getBody().path("assigneeId").asText())
        .isEqualTo(adminId.toString());
    ResponseEntity<JsonNode> second = patch(otherAdmin, "{\"status\":\"IN_REVIEW\"}");
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.getBody().path("code").asText()).isEqualTo("already-claimed");
    assertThat(patch(admin, "{\"status\":\"OPEN\"}").getBody().path("status").asText())
        .isEqualTo("OPEN");
  }

  @Test
  void aNonExistentSectionIs422CitationNotVerifiableAndNothingIsSaved() {
    patch(admin, "{\"status\":\"IN_REVIEW\"}");

    ResponseEntity<JsonNode> response =
        patch(admin, approve("XV-LAB:999@original", "this section does not exist in the corpus"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().path("code").asText()).isEqualTo("citation-not-verifiable");
    assertThat(get(admin, "/api/v1/review-items/" + item).getBody().path("status").asText())
        .isEqualTo("IN_REVIEW");
  }

  @Test
  void aQuoteThatIsNotInTheSectionIs422Too() {
    patch(admin, "{\"status\":\"IN_REVIEW\"}");

    ResponseEntity<JsonNode> response =
        patch(
            admin,
            approve(FictionalCorpus.key("5"), "a quote that the fictional section never says"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void aVerifiedCitationApprovesTheItemAndTheDecisionIsAudited() {
    patch(admin, "{\"status\":\"IN_REVIEW\"}");

    ResponseEntity<JsonNode> response =
        patch(
            admin,
            approve(
                FictionalCorpus.key("5"),
                "shall give a worker written notice of dismissal at least fourteen days"));

    assertThat(response.getStatusCode())
        .as(String.valueOf(response.getBody()))
        .isEqualTo(HttpStatus.OK);
    JsonNode decision = response.getBody().path("decision");
    assertThat(response.getBody().path("status").asText()).isEqualTo("APPROVED");
    assertThat(decision.path("decision").asText()).isEqualTo("APPROVED_WITH_CITATION");
    assertThat(decision.path("citation").path("sectionKey").asText())
        .isEqualTo(FictionalCorpus.key("5"));
    assertThat(decision.path("citation").path("verification").path("status").asText())
        .isEqualTo("VERIFIED");
    assertThat(decision.path("decidedBy").asText()).isEqualTo(adminId.toString());
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'REVIEW_DECISION'"
                        + " and target_id = :id and actor_id = :admin")
                .param("id", item.toString())
                .param("admin", adminId)
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(2); // the claim and the decision

    ResponseEntity<JsonNode> again = patch(admin, "{\"decision\":\"REJECTED\",\"note\":\"x\"}");
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(again.getBody().path("code").asText()).isEqualTo("already-decided");
  }

  @Test
  void anApprovalWithoutACitationOrARejectionNeedsANote() {
    patch(admin, "{\"status\":\"IN_REVIEW\"}");

    assertThat(patch(admin, "{\"decision\":\"REJECTED\"}").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ResponseEntity<JsonNode> rejected =
        patch(admin, "{\"decision\":\"REJECTED\",\"note\":\"The finding is wrong.\"}");
    assertThat(rejected.getBody().path("status").asText()).isEqualTo("REJECTED");
    assertThat(rejected.getBody().path("decision").path("citation").isNull()).isTrue();
  }
}
