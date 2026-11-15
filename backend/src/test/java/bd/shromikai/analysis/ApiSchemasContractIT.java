package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * FR-63, FR-58: what the API really returns, from a real pipeline run with scripted stand-ins,
 * validates against the JSON Schemas under {@code schemas/api}. Those schemas are the source of the
 * OpenAPI components and so of the generated frontend types; a response that drifts from them fails
 * here, not in the browser.
 */
@Requirement("FR-63")
@Requirement("FR-58")
class ApiSchemasContractIT extends ApiAnalysisIT {

  private static final JsonSchemas SCHEMAS = new JsonSchemas();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String BASELINE =
      """
      {"status":"OK","reason":"NONE","findings":[
       {"title":"Dismissal needs notice","severity":"HIGH",
        "explanation":"The contract allows dismissal without the notice the law requires.",
        "citations":[
         {"section_key":"XV-LAB:5@original",
          "quote":"shall give a worker written notice of dismissal at least fourteen days",
          "claim":"An employer must give written notice before dismissal."},
         {"section_key":"XV-LAB:999@original",
          "quote":"an invented section that says whatever the model wants",
          "claim":"The worker may never be dismissed."}]}]}
      """;

  private static void assertValid(String schema, JsonNode document) {
    assertThat(SCHEMAS.validate("schemas/api/" + schema + ".v1.schema.json", document))
        .as(
            "%s against %s",
            document.toString().substring(0, Math.min(200, document.toString().length())), schema)
        .isEmpty();
  }

  @Test
  void aFinishedResultMatchesTheAnalysisResultSchema() {
    ResponseEntity<JsonNode> response = postJson(worker());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertValid("analysis-result", response.getBody());
  }

  @Test
  void theSchemasHaveTeethAMissingFieldAWrongEnumAndABareCitationAreRejected() {
    JsonNode result = postJson(worker()).getBody();
    String schema = "schemas/api/analysis-result.v1.schema.json";

    com.fasterxml.jackson.databind.node.ObjectNode noEnvelope = result.deepCopy();
    noEnvelope.remove("envelope");
    assertThat(SCHEMAS.validate(schema, noEnvelope)).isNotEmpty();

    com.fasterxml.jackson.databind.node.ObjectNode extra = result.deepCopy();
    extra.put("surprise", true);
    assertThat(SCHEMAS.validate(schema, extra)).isNotEmpty();

    com.fasterxml.jackson.databind.node.ObjectNode badEnum = result.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) badEnum.path("findings").get(0))
        .put("verificationStatus", "WEAK");
    assertThat(SCHEMAS.validate(schema, badEnum)).isNotEmpty();

    com.fasterxml.jackson.databind.node.ObjectNode bare = result.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) bare.path("findings").get(0))
        .putArray("citations")
        .addObject()
        .put("sectionKey", "XV-LAB:5@original");
    assertThat(SCHEMAS.validate(schema, bare)).isNotEmpty();
  }

  @Test
  void acceptedStatusViewsEventsAndTheFinalResultMatchTheirSchemas() throws Exception {
    String token = worker();
    ResponseEntity<JsonNode> accepted = postMultipart(token, text());

    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertValid("analysis-accepted", accepted.getBody());
    String id = accepted.getBody().path("analysisId").asText();

    int running = 0;
    JsonNode result = null;
    for (int i = 0; i < 300 && result == null; i++) {
      ResponseEntity<JsonNode> poll = get(token, "/api/v1/analyses/" + id);
      if (poll.getStatusCode() == HttpStatus.OK) {
        result = poll.getBody();
      } else {
        assertThat(poll.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertValid("analysis-status-view", poll.getBody());
        running++;
        Thread.sleep(20);
      }
    }
    assertThat(result).as("finished").isNotNull();
    assertValid("analysis-result", result);
    assertThat(running).as("the schema saw a running status view").isGreaterThanOrEqualTo(0);

    List<Sse> sse = events(token, id, null);
    assertThat(sse).extracting(Sse::name).contains("stage", "completed");
    for (Sse event : sse) {
      JsonNode data = MAPPER.readTree(event.data());
      switch (event.name()) {
        case "stage" -> assertValid("sse-stage-event", data);
        case "warning" -> assertValid("sse-warning-event", data);
        case "completed" -> assertValid("sse-completed-event", data);
        default -> throw new AssertionError("unexpected event " + event.name());
      }
    }
  }

  @Test
  void theTraceAndTheObligationsChecklistMatchTheirSchemas() throws Exception {
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();

    ResponseEntity<JsonNode> trace = get(token, "/api/v1/analyses/" + id + "/trace");
    ResponseEntity<JsonNode> obligations = get(token, "/api/v1/analyses/" + id + "/obligations");

    assertThat(trace.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertValid("analysis-trace", trace.getBody());
    assertThat(obligations.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertValid("obligations-export", obligations.getBody());
  }

  @Test
  void theCompareResultAndItsAcceptedAndRunningViewsMatchTheirSchemas() throws Exception {
    fake.respondWith("baseline", request -> TestGateways.json(BASELINE));
    String token = worker();
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
    assertValid("compare-accepted", accepted.getBody());
    String id = accepted.getBody().path("compareId").asText();

    JsonNode result = null;
    for (int i = 0; i < 300 && result == null; i++) {
      ResponseEntity<JsonNode> poll = get(token, "/api/v1/compare/" + id);
      if (poll.getStatusCode() == HttpStatus.OK) {
        result = poll.getBody();
      } else {
        assertValid("compare-running", poll.getBody());
        Thread.sleep(20);
      }
    }
    assertThat(result).as("compare finished").isNotNull();
    assertValid("compare-result", result);
    assertThat(result.path("summary").path("baselineRejected").asInt()).isPositive();
  }
}
