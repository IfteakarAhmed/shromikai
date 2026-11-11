package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.MutableClockConfig;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-58, FR-54: multipart and JSON input, the synchronous and the asynchronous path, events in
 * order with resume, the Bearer-only rule for events, and the 30-minute result life.
 */
@Requirement("FR-58")
@Requirement("FR-54")
class AnalysesApiIT extends ApiAnalysisIT {

  @Test
  void shortJsonTextIsAnsweredSynchronouslyWithTheResult() {
    ResponseEntity<JsonNode> response = postJson(worker());

    assertThat(response.getStatusCode())
        .as(String.valueOf(response.getBody()))
        .isEqualTo(HttpStatus.OK);
    JsonNode result = response.getBody();
    assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(result.path("findings")).isNotEmpty();
    assertThat(result.path("memo").path("sections")).hasSize(8);
    assertThat(result.path("envelope").path("algorithm").asText()).isEqualTo("HMAC-SHA256");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void multipartInputIsAcceptedWith202AndFinishesInTheBackground() throws Exception {
    String token = worker();

    ResponseEntity<JsonNode> accepted = postMultipart(token, text());

    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String id = accepted.getBody().path("analysisId").asText();
    assertThat(accepted.getBody().path("eventsUrl").asText())
        .isEqualTo("/api/v1/analyses/" + id + "/events");
    JsonNode result = awaitResult(token, id);
    assertThat(result.path("analysisId").asText()).isEqualTo(id);
    assertThat(result.path("input").path("kind").asText()).isEqualTo("TEXT");
  }

  @Test
  void textOverTheSynchronousLimitIsAnswered202EvenWhenItWouldBeFast() throws Exception {
    String token = worker();
    String longText =
        String.join("\n\n", java.util.Collections.nCopies(5, SyntheticContracts.text("syn-bn-01")));
    assertThat(longText.length()).isGreaterThan(8_000);

    ResponseEntity<JsonNode> response =
        postJson(token, Map.of("text", longText, "side", "WORKER", "outputLanguage", "bn"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(
            awaitResult(token, response.getBody().path("analysisId").asText())
                .path("status")
                .asText())
        .isEqualTo("SUCCEEDED");
  }

  @Test
  void eventsArriveInOrderEndWithCompletedAndCarryBanglaMessages() throws Exception {
    String token = worker();
    String id = postMultipart(token, text()).getBody().path("analysisId").asText();

    List<Sse> events = events(token, id, null);

    assertThat(events).isNotEmpty();
    for (int i = 0; i < events.size(); i++) {
      assertThat(events.get(i).id()).isEqualTo(i + 1);
    }
    assertThat(events.get(events.size() - 1).name()).isEqualTo("completed");
    assertThat(events.get(0).name()).isEqualTo("stage");
    assertThat(events.get(0).data()).contains("\"stage\":\"INTAKE\"").contains("চুক্তি পড়া হচ্ছে");
    List<String> stages =
        events.stream()
            .filter(e -> e.name().equals("stage"))
            .map(e -> e.data().replaceAll(".*\"stage\":\"([A-Z]+)\".*", "$1"))
            .toList();
    List<String> order =
        List.of(
            "INTAKE",
            "REDACT",
            "CLASSIFY",
            "RETRIEVE",
            "RULES",
            "RISK",
            "VERIFY",
            "CORRECT",
            "COMPOSE",
            "COMPLETE");
    // RETRIEVE and RULES may interleave; every other stage keeps its place
    assertThat(stages).containsSubsequence("INTAKE", "REDACT", "CLASSIFY");
    assertThat(stages).containsSubsequence("CLASSIFY", "RISK", "VERIFY", "COMPOSE", "COMPLETE");
    assertThat(order).containsAll(stages.stream().distinct().toList());
  }

  @Test
  void reconnectingWithLastEventIdReplaysOnlyTheMissedEvents() throws Exception {
    String token = worker();
    String id = postMultipart(token, text()).getBody().path("analysisId").asText();
    List<Sse> all = events(token, id, null);

    List<Sse> resumed = events(token, id, 3);

    assertThat(resumed).first().extracting(Sse::id).isEqualTo(4);
    assertThat(resumed).isEqualTo(all.subList(3, all.size()));
  }

  @Test
  void eventsNeedABearerHeaderAndRefuseATokenInTheQueryString() {
    String token = worker();
    String id = postMultipart(token, text()).getBody().path("analysisId").asText();

    ResponseEntity<String> anonymous =
        http.exchange(
            "/api/v1/analyses/" + id + "/events", HttpMethod.GET, HttpEntity.EMPTY, String.class);
    ResponseEntity<String> inQuery =
        http.exchange(
            "/api/v1/analyses/" + id + "/events?access_token=" + token,
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            String.class);

    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(inQuery.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theResultIsThereAt29MinutesAndGoneWith410At31() {
    UUID user = UUID.randomUUID();
    String id = postJson(tokenFor(user)).getBody().path("analysisId").asText();

    MutableClockConfig.CLOCK.advance(Duration.ofMinutes(29));
    assertThat(get(tokenFor(user), "/api/v1/analyses/" + id).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    MutableClockConfig.CLOCK.advance(Duration.ofMinutes(2));
    ResponseEntity<JsonNode> expired = get(tokenFor(user), "/api/v1/analyses/" + id);
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(expired.getBody().path("code").asText()).isEqualTo("analysis-expired");
    assertThat(get(worker(), "/api/v1/analyses/" + id).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deletePurgesTheResultAndTheOwnerThenGets410() {
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();

    ResponseEntity<Void> deleted =
        http.exchange(
            "/api/v1/analyses/" + id,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token)),
            Void.class);
    ResponseEntity<JsonNode> after = get(token, "/api/v1/analyses/" + id);

    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(after.getBody().path("code").asText()).isEqualTo("analysis-expired");
  }

  /** FR-48, FR-62: the trace shows the cost of the analysis and its cache usage, and no text. */
  @Test
  void theTraceShowsCostAndCacheUsageAndNoContractText() {
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();

    ResponseEntity<JsonNode> response = get(token, "/api/v1/analyses/" + id + "/trace");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode trace = response.getBody();
    assertThat(trace.path("analysisId").asText()).isEqualTo(id);
    assertThat(trace.path("events").get(0).path("stage").asText()).isEqualTo("INTAKE");
    assertThat(trace.path("totals").has("costUsdMicros")).isTrue();
    assertThat(trace.path("totals").has("cacheReadTokens")).isTrue();
    assertThat(trace.path("totals").has("cacheWriteTokens")).isTrue();
    JsonNode risk = null;
    for (JsonNode event : trace.path("events")) {
      if (event.path("stage").asText().equals("RISK")) {
        risk = event;
      }
    }
    assertThat(risk).isNotNull();
    assertThat(risk.has("cacheReadTokens")).isTrue();
    assertThat(risk.path("tokensIn").isNumber()).isTrue();
    assertThat(trace.toString()).doesNotContain(SyntheticContracts.marker("syn-en-01"));
  }

  /** FR-61: an analysis with a finding no verified citation backs hands the worker a ticket. */
  @Test
  void anUncertainFindingGivesTheWorkerAReviewTicketTheyCanFollow() {
    String token = worker();
    JsonNode result = null;
    for (String contract : SyntheticContracts.IDS) {
      JsonNode candidate =
          postJson(
                  token,
                  Map.of(
                      "text",
                      SyntheticContracts.text(contract),
                      "side",
                      "WORKER",
                      "outputLanguage",
                      contract.startsWith("syn-bn") ? "bn" : "en"))
              .getBody();
      if (candidate.path("reviewTicket").isObject()) {
        result = candidate;
        break;
      }
    }
    assertThat(result).as("a synthetic contract with an uncertain finding").isNotNull();
    JsonNode ticket = result.path("reviewTicket");
    HttpHeaders headers = bearer(token);
    headers.set("X-Ticket-Token", ticket.path("token").asText());

    ResponseEntity<JsonNode> status =
        http.exchange(
            "/api/v1/review-tickets/" + ticket.path("ticketId").asText(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);

    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(status.getBody().path("items")).isNotEmpty();
    assertThat(status.getBody().path("items").get(0).path("status").asText()).isEqualTo("OPEN");
    long uncertain =
        java.util.stream.StreamSupport.stream(result.path("findings").spliterator(), false)
            .filter(f -> f.path("verificationStatus").asText().equals("UNCERTAIN"))
            .count();
    assertThat((long) status.getBody().path("items").size()).isGreaterThanOrEqualTo(uncertain);
  }
}
