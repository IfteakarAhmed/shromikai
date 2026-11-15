package bd.shromikai.api;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.IntegrationTest;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * FR-60, FR-61, FR-63: the review queue, a review item and the ticket the worker reads match the
 * JSON Schemas under {@code schemas/api}, before and after a decision with a verified citation.
 */
@IntegrationTest
@Requirement("FR-60")
@Requirement("FR-61")
@Requirement("FR-63")
class ReviewApiSchemasIT {

  private static final String TOKEN = "schema-ticket-token-0123456789abcdef0123";
  private static final JsonSchemas SCHEMAS = new JsonSchemas();

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired JdbcClient jdbc;
  @Autowired PasswordEncoder encoder;

  private UUID analysis;
  private UUID ticket;
  private UUID item;
  private UUID adminId;

  @BeforeEach
  void anOpenItem() {
    FictionalCorpus.load(jdbc);
    adminId =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            "schema-admin-" + UUID.randomUUID() + "@example.test",
            IdentityFixtures.PASSWORD,
            Role.ADMIN,
            false);
    analysis = UUID.randomUUID();
    ticket = UUID.randomUUID();
    item = UUID.randomUUID();
    jdbc.sql(
            """
            insert into analysis_run (id, subject_kind, subject_hash, client_kind, side,
                                      output_language, input_kind, page_count, status,
                                      pipeline_version)
            values (:id, 'USER', repeat('c', 64), 'API', 'WORKER', 'en', 'TEXT', 1, 'SUCCEEDED', 'p7-1')
            """)
        .param("id", analysis)
        .update();
    Instant now = Instant.now();
    jdbc.sql(
            """
            insert into review_item (id, kind, analysis_id, finding_id, clause_type, finding_kind,
                                     severity, side, risk_summary, facts, reason_codes, status,
                                     created_at, expires_at, ticket_id, ticket_token_sha256)
            values (:id, 'FINDING', :analysis, 'f1', 'TERMINATION', 'CONFLICT', 'HIGH',
                    'WORKER', 'Short notice of dismissal.', '[]'::jsonb, array['SUPPORT_PARTIAL'],
                    'OPEN', :created, :expires, :ticket, :hash)
            """)
        .param("id", item)
        .param("analysis", analysis)
        .param("created", Timestamp.from(now))
        .param("expires", Timestamp.from(now.plus(Duration.ofDays(30))))
        .param("ticket", ticket)
        .param("hash", Hashing.sha256Hex(TOKEN))
        .update();
  }

  @AfterEach
  void clean() {
    jdbc.sql("delete from analysis_run").update();
    jdbc.sql("delete from app_user where email like 'schema-admin-%'").update();
    FictionalCorpus.clean(jdbc);
  }

  private HttpHeaders adminHeaders() {
    HttpHeaders headers = bearer(jwt.issueUser(adminId, Role.ADMIN, null, false).value());
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<JsonNode> patch(String body) {
    return http.exchange(
        "/api/v1/review-items/" + item,
        HttpMethod.PATCH,
        new HttpEntity<>(body, adminHeaders()),
        JsonNode.class);
  }

  private static void assertValid(String schema, JsonNode document) {
    assertThat(SCHEMAS.validate("schemas/api/" + schema + ".v1.schema.json", document))
        .as(schema)
        .isEmpty();
  }

  private JsonNode ticketBody() {
    HttpHeaders headers =
        bearer(jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value());
    headers.set("X-Ticket-Token", TOKEN);
    return http.exchange(
            "/api/v1/review-tickets/" + ticket,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class)
        .getBody();
  }

  @Test
  void theQueueAnItemAndTheTicketMatchTheirSchemasOpenAndDecided() {
    ResponseEntity<JsonNode> page =
        http.exchange(
            "/api/v1/review-items?status=OPEN",
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            JsonNode.class);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page.getBody().path("items")).isNotEmpty();
    assertValid("review-item-page", page.getBody());

    ResponseEntity<JsonNode> one =
        http.exchange(
            "/api/v1/review-items/" + item,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            JsonNode.class);
    assertValid("review-item", one.getBody());
    assertValid("review-ticket-status", ticketBody());

    ResponseEntity<JsonNode> claimed = patch("{\"status\":\"IN_REVIEW\"}");
    assertValid("review-item", claimed.getBody());
    ResponseEntity<JsonNode> decided =
        patch(
            "{\"decision\":\"APPROVED_WITH_CITATION\",\"sectionKey\":\""
                + FictionalCorpus.key("5")
                + "\",\"quote\":\"shall give a worker written notice of dismissal at least fourteen days\","
                + "\"claim\":\"The section requires fourteen days of notice.\",\"note\":\"checked\"}");
    assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertValid("review-item", decided.getBody());
    assertValid("review-ticket-status", ticketBody());
  }
}
