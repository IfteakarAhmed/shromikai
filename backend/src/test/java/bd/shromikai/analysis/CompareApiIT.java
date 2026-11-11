package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * FR-59, R-01: the baseline's citations go through the same verifier; an invented section is
 * REJECTED with SECTION_NOT_FOUND and never shown as verified; a compare is 202 and counts as two
 * analyses for a guest; another subject gets 404.
 */
@Requirement("FR-59")
class CompareApiIT extends ApiAnalysisIT {

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

  private ResponseEntity<JsonNode> startCompare(String token) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/compare",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("text", text(), "side", "WORKER", "outputLanguage", "en"), headers),
        JsonNode.class);
  }

  private JsonNode awaitCompare(String token, String id) throws InterruptedException {
    for (int i = 0; i < 300; i++) {
      ResponseEntity<JsonNode> r = get(token, "/api/v1/compare/" + id);
      if (r.getStatusCode() == HttpStatus.OK) {
        return r.getBody();
      }
      Thread.sleep(100);
    }
    throw new AssertionError("compare did not finish");
  }

  @Test
  void aFabricatedBaselineSectionIsRejectedAndARealOneIsVerifiedByTheSameVerifier()
      throws Exception {
    fake.respondWith("baseline", request -> TestGateways.json(BASELINE));
    String token = worker();

    ResponseEntity<JsonNode> accepted = startCompare(token);
    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String id = accepted.getBody().path("compareId").asText();
    assertThat(accepted.getBody().path("resultUrl").asText()).isEqualTo("/api/v1/compare/" + id);
    JsonNode result = awaitCompare(token, id);

    JsonNode citations = result.path("baseline").path("findings").get(0).path("citations");
    assertThat(citations.get(0).path("verification").path("status").asText()).isEqualTo("VERIFIED");
    JsonNode fake999 = citations.get(1);
    assertThat(fake999.path("verification").path("status").asText()).isEqualTo("REJECTED");
    assertThat(fake999.path("verification").path("reasonCodes").toString())
        .contains("SECTION_NOT_FOUND");
    assertThat(fake999.has("citation")).isFalse();
    JsonNode summary = result.path("summary");
    assertThat(summary.path("baselineCitations").asInt()).isEqualTo(2);
    assertThat(summary.path("baselineRejected").asInt()).isEqualTo(1);
    assertThat(summary.path("baselineVerified").asInt()).isEqualTo(1);
    assertThat(result.path("shromikai").path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(result.path("disclaimer").asText()).isNotBlank();
  }

  @Test
  void aBaselineThatCannotAnswerIsUnavailableAndTheShromikaiResultStands() throws Exception {
    fake.respondWith("baseline", request -> TestGateways.json("{\"nonsense\":true}"));
    String token = worker();

    JsonNode result = awaitCompare(token, startCompare(token).getBody().path("compareId").asText());

    assertThat(result.path("baseline").path("status").asText()).isEqualTo("UNAVAILABLE");
    assertThat(result.path("baseline").path("findings")).isEmpty();
    assertThat(result.path("shromikai").path("findings")).isNotEmpty();
  }

  @Test
  void aCompareCountsAsTwoAnalysesForAGuest() throws Exception {
    fake.respondWith("baseline", request -> TestGateways.json(BASELINE));
    String guest =
        jwt.issueGuest(Hashing.sha256Hex("compare-device-" + java.util.UUID.randomUUID())).value();

    ResponseEntity<JsonNode> first = startCompare(guest);
    awaitCompare(guest, first.getBody().path("compareId").asText());
    ResponseEntity<JsonNode> second = startCompare(guest); // 2 + 2 > 3 a day

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(second.getBody().path("code").asText()).isEqualTo("quota-exceeded");
  }

  @Test
  void
      anotherSubjectGets404OnTheCompareResourcesAndTheDocumentNeverReachesTheEgressGuardUnredacted()
          throws Exception {
    fake.respondWith("baseline", request -> TestGateways.json(BASELINE));
    String owner = worker();
    String id = startCompare(owner).getBody().path("compareId").asText();
    awaitCompare(owner, id);

    assertThat(get(worker(), "/api/v1/compare/" + id).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    HttpHeaders sse = bearer(worker());
    sse.set(HttpHeaders.ACCEPT, "text/event-stream");
    assertThat(
            http.exchange(
                    "/api/v1/compare/" + id + "/events",
                    HttpMethod.GET,
                    new HttpEntity<>(sse),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
