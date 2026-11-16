package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.memo.PdfRenderer;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * FR-73, KPI-13: the REST API writes usage events the way MCP does. A finished analysis writes one
 * {@code ANALYSIS} event with its cost, a finished compare one {@code COMPARE} event, a memo render
 * one {@code MEMO_RENDER} event; a failed analysis writes none (an event is a billable unit). The
 * event holds kind, units, cost and organisation, never content (R-02).
 */
@Requirement("FR-73")
@Import(RestUsageEventsIT.StubPdf.class)
class RestUsageEventsIT extends ApiAnalysisIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class StubPdf {
    @Bean
    @Primary
    PdfRenderer stubPdfRenderer() {
      return html -> "%PDF-1.7 stub".getBytes(StandardCharsets.UTF_8);
    }
  }

  private UUID orgId;
  private String token;

  @BeforeEach
  void anOrganisationAndItsMember() {
    orgId =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values ('Events Org', 'HR', 'TEAM')"
                    + " returning id")
            .query(UUID.class)
            .single();
    token = jwt.issueUser(UUID.randomUUID(), Role.HR, orgId, false).value();
  }

  @AfterEach
  void removeTheOrganisation() {
    jdbc.sql("delete from analysis_run where org_id = :o").param("o", orgId).update();
    jdbc.sql("delete from usage_event where org_id = :o").param("o", orgId).update();
    jdbc.sql("delete from organisation where id = :o").param("o", orgId).update();
  }

  private List<Map<String, Object>> events(String kind) {
    return jdbc.sql(
            "select kind, units, cost_usd_micros, user_id, api_key_id from usage_event"
                + " where org_id = :o and kind = :k")
        .param("o", orgId)
        .param("k", kind)
        .query()
        .listOfRows();
  }

  /** The event is written as the job ends, a moment after the result becomes readable. */
  private List<Map<String, Object>> eventsEventually(String kind) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      List<Map<String, Object>> rows = events(kind);
      if (!rows.isEmpty()) {
        return rows;
      }
      Thread.sleep(50);
    }
    return events(kind);
  }

  @Test
  void aFinishedAnalysisWritesOneEventWithItsCost() throws Exception {
    String id = postJson(token).getBody().path("analysisId").asText();
    JsonNode result = awaitResult(token, id);

    List<Map<String, Object>> rows = eventsEventually("ANALYSIS");

    assertThat(rows).hasSize(1);
    assertThat(((Number) rows.get(0).get("units")).intValue()).isEqualTo(1);
    long recorded = ((Number) rows.get(0).get("cost_usd_micros")).longValue();
    assertThat(recorded)
        .isEqualTo(
            jdbc.sql("select cost_usd_micros from analysis_run where id = :id")
                .param("id", UUID.fromString(id))
                .query(Long.class)
                .single());
    assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(events("COMPARE")).isEmpty();
  }

  @Test
  void aFinishedCompareWritesOneCompareEventAndNoAnalysisEvent() throws Exception {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> accepted =
        http.exchange(
            "/api/v1/compare",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("text", text(), "side", "WORKER", "outputLanguage", "en"), headers),
            JsonNode.class);
    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String id = accepted.getBody().path("compareId").asText();
    for (int i = 0; i < 300; i++) {
      if (get(token, "/api/v1/compare/" + id).getStatusCode() == HttpStatus.OK) {
        break;
      }
      Thread.sleep(50);
    }

    assertThat(eventsEventually("COMPARE")).hasSize(1);
    assertThat(events("ANALYSIS")).isEmpty();
  }

  @Test
  void aMemoRenderWritesOneMemoRenderEvent() throws Exception {
    String id = postJson(token).getBody().path("analysisId").asText();
    JsonNode result = awaitResult(token, id);
    ObjectNode payload = result.deepCopy();
    payload.remove("envelope");
    ObjectNode envelope = payload.objectNode();
    envelope.set("payload", payload);
    envelope.put("keyId", result.path("envelope").path("keyId").asText());
    envelope.put("algorithm", result.path("envelope").path("algorithm").asText());
    envelope.put("expiresAt", result.path("envelope").path("expiresAt").asText());
    envelope.put("signature", result.path("envelope").path("signature").asText());
    ObjectNode body = envelope.objectNode();
    body.set("envelope", envelope);
    body.put("format", "HTML");
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<byte[]> rendered =
        http.exchange(
            "/api/v1/memos/render", HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);

    assertThat(rendered.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(events("MEMO_RENDER")).hasSize(1);
  }

  @Test
  void aRefusedRequestWritesNoEvent() {
    ResponseEntity<JsonNode> refused = postJson(token, Map.of("side", "WORKER"));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(events("ANALYSIS")).isEmpty();
  }
}
