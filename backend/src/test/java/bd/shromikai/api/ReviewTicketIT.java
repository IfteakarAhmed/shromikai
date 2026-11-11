package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.MutableClockConfig;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * FR-61: a ticket is read with its secret token (wrong token 404); a decided item shows its
 * decision and a verified citation; a ticket stays valid until the last of its items expires, so an
 * item decided on day 29 is still visible on day 58, and the ticket is 410 once all have expired.
 */
@IntegrationTest
@Import(MutableClockConfig.class)
@Requirement("FR-61")
class ReviewTicketIT {

  private static final String TOKEN = "ticket-token-for-the-test-0123456789abcdef";

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private UUID analysis;
  private UUID ticket;
  private UUID firstItem;
  private UUID adminId;
  private Instant created;

  @BeforeEach
  void aTicketWithTwoItems() {
    FictionalCorpus.load(jdbc);
    created = MutableClockConfig.CLOCK.instant();
    adminId =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            "ticket-admin-" + UUID.randomUUID() + "@example.test",
            IdentityFixtures.PASSWORD,
            Role.ADMIN,
            false);
    analysis = UUID.randomUUID();
    ticket = UUID.randomUUID();
    jdbc.sql(
            """
            insert into analysis_run (id, subject_kind, subject_hash, client_kind, side,
                                      output_language, input_kind, page_count, status,
                                      pipeline_version)
            values (:id, 'USER', repeat('b', 64), 'API', 'WORKER', 'en', 'TEXT', 1, 'SUCCEEDED', 'p7-1')
            """)
        .param("id", analysis)
        .update();
    firstItem = item("f1");
    item("f2");
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from analysis_run").update();
    jdbc.sql("delete from app_user where email like 'ticket-%'").update();
    FictionalCorpus.clean(jdbc);
  }

  private UUID item(String finding) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into review_item (id, kind, analysis_id, finding_id, clause_type, finding_kind,
                                     severity, side, risk_summary, facts, reason_codes, status,
                                     created_at, expires_at, ticket_id, ticket_token_sha256)
            values (:id, 'FINDING', :analysis, :finding, 'TERMINATION', 'CONFLICT', 'HIGH',
                    'WORKER', 'Short notice of dismissal.', '[]'::jsonb, array['NOT_VERIFIED'],
                    'OPEN', :created, :expires, :ticket, :hash)
            """)
        .param("id", id)
        .param("analysis", analysis)
        .param("finding", finding)
        .param("created", Timestamp.from(created))
        .param("expires", Timestamp.from(created.plus(Duration.ofDays(30))))
        .param("ticket", ticket)
        .param("hash", Hashing.sha256Hex(TOKEN))
        .update();
    return id;
  }

  private ResponseEntity<JsonNode> readTicket(String token) {
    HttpHeaders headers =
        bearer(jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value());
    if (token != null) {
      headers.set("X-Ticket-Token", token);
    }
    return http.exchange(
        "/api/v1/review-tickets/" + ticket,
        HttpMethod.GET,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> admin(UUID item, String body) {
    HttpHeaders headers = bearer(jwt.issueUser(adminId, Role.ADMIN, null, false).value());
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/review-items/" + item,
        HttpMethod.PATCH,
        new HttpEntity<>(body, headers),
        JsonNode.class);
  }

  private static JsonNode byFinding(JsonNode ticket, String finding) {
    for (JsonNode item : ticket.path("items")) {
      if (item.path("findingId").asText().equals(finding)) {
        return item;
      }
    }
    throw new AssertionError("no item " + finding);
  }

  @Test
  void aWrongOrMissingTokenIs404AndTheRightOneListsTheItems() {
    assertThat(readTicket("not-the-token").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(readTicket(null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<JsonNode> ok = readTicket(TOKEN);

    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ok.getBody().path("items")).hasSize(2);
    assertThat(ok.getBody().path("items").get(0).path("status").asText()).isEqualTo("OPEN");
    assertThat(ok.getBody().path("items").get(0).path("decision").isNull()).isTrue();
    assertThat(ok.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void aDecidedItemShowsTheDecisionAndAVerifiedCitation() {
    admin(firstItem, "{\"status\":\"IN_REVIEW\"}");
    admin(
        firstItem,
        "{\"decision\":\"APPROVED_WITH_CITATION\",\"sectionKey\":\""
            + FictionalCorpus.key("5")
            + "\",\"quote\":\"shall give a worker written notice of dismissal at least fourteen days\","
            + "\"claim\":\"The section requires fourteen days of notice.\",\"note\":\"checked\"}");

    JsonNode first = byFinding(readTicket(TOKEN).getBody(), "f1");

    assertThat(first.path("status").asText()).isEqualTo("APPROVED");
    assertThat(first.path("decision").asText()).isEqualTo("APPROVED_WITH_CITATION");
    assertThat(first.path("citation").path("verification").path("status").asText())
        .isEqualTo("VERIFIED");
    assertThat(first.path("note").asText()).isEqualTo("checked");
  }

  @Test
  void anItemDecidedOnDay29IsStillVisibleOnDay58AndTheTicketIs410WhenAllHaveExpired() {
    MutableClockConfig.CLOCK.advance(Duration.ofDays(29));
    admin(firstItem, "{\"status\":\"IN_REVIEW\"}");
    admin(firstItem, "{\"decision\":\"REJECTED\",\"note\":\"Not a real risk.\"}");

    MutableClockConfig.CLOCK.advance(
        Duration.ofDays(29)); // day 58: the undecided item expired on 30
    ResponseEntity<JsonNode> day58 = readTicket(TOKEN);
    assertThat(day58.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byFinding(day58.getBody(), "f1").path("status").asText()).isEqualTo("REJECTED");

    MutableClockConfig.CLOCK.advance(Duration.ofDays(2)); // day 60: decided day 29 + 30 = 59
    ResponseEntity<JsonNode> day60 = readTicket(TOKEN);
    assertThat(day60.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(day60.getBody().path("code").asText()).isEqualTo("ticket-expired");
  }
}
