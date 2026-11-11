package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.MutableClockConfig;
import bd.shromikai.testsupport.P6Stubs;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Base of the integration tests of the public analyses API (FR-58): the real pipeline with scripted
 * stand-ins for the models and a fictional act (pack xx, R-10), a clock the test can move, and
 * helpers to start an analysis as a given subject. The stand-ins are not recorded model output.
 */
@IntegrationTest
@Import(MutableClockConfig.class)
abstract class ApiAnalysisIT {

  @Autowired protected TestRestTemplate http;
  @Autowired protected JwtService jwt;
  @Autowired protected FakeChatPort fake;
  @Autowired protected JdbcClient jdbc;
  @LocalServerPort protected int port;

  @BeforeEach
  void installStandIns() {
    FictionalCorpus.load(jdbc);
    fake.reset();
    P6Stubs.install(fake);
  }

  @Autowired protected JobStore jobStore;

  /**
   * Waits for background analyses a test left running before the corpus and runs are deleted.
   * Cleaning under a running pipeline caused a NullPointerException in retrieval and starved the
   * test pool of connections (the intermittent 30 s Hikari timeout seen in P7).
   */
  @AfterEach
  void cleanDatabase() throws InterruptedException {
    for (int i = 0; i < 300 && jobStore.countRunning(j -> true) > 0; i++) {
      Thread.sleep(100);
    }
    Thread.sleep(300); // the last run-store writes follow the job's completion
    jdbc.sql("delete from analysis_run").update();
    FictionalCorpus.clean(jdbc);
  }

  protected String worker() {
    return tokenFor(UUID.randomUUID());
  }

  /** A fresh access token of the same user, for tests that move the clock. */
  protected String tokenFor(UUID user) {
    return jwt.issueUser(user, Role.WORKER, null, false).value();
  }

  protected static String text() {
    return SyntheticContracts.text("syn-en-01");
  }

  protected ResponseEntity<JsonNode> postJson(String token, Map<String, Object> body) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/analyses", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
  }

  protected ResponseEntity<JsonNode> postJson(String token) {
    return postJson(token, Map.of("text", text(), "side", "WORKER", "outputLanguage", "en"));
  }

  /** A multipart request with the contract as a {@code text} part (always answered 202). */
  protected ResponseEntity<JsonNode> postMultipart(String token, String contract) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpHeaders optionsHeaders = new HttpHeaders();
    optionsHeaders.setContentType(MediaType.APPLICATION_JSON);
    HttpHeaders textHeaders = new HttpHeaders();
    textHeaders.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
    LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add(
        "options",
        new HttpEntity<>("{\"side\":\"WORKER\",\"outputLanguage\":\"en\"}", optionsHeaders));
    parts.add("text", new HttpEntity<>(contract, textHeaders));
    return http.exchange(
        "/api/v1/analyses", HttpMethod.POST, new HttpEntity<>(parts, headers), JsonNode.class);
  }

  protected ResponseEntity<JsonNode> get(String token, String path) {
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
  }

  /** Polls until the analysis has finished (status 200) or fails the test after 30 s. */
  protected JsonNode awaitResult(String token, String id) throws InterruptedException {
    for (int i = 0; i < 300; i++) {
      ResponseEntity<JsonNode> response = get(token, "/api/v1/analyses/" + id);
      if (response.getStatusCode().value() == 200) {
        return response.getBody();
      }
      Thread.sleep(100);
    }
    throw new AssertionError("analysis did not finish");
  }

  /** One server-sent event as the client sees it. */
  protected record Sse(int id, String name, String data) {}

  /** Reads the event stream of an analysis until it closes. */
  protected List<Sse> events(String token, String id, Integer lastEventId) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/analyses/" + id + "/events"))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(60));
    if (lastEventId != null) {
      request.header("Last-Event-ID", String.valueOf(lastEventId));
    }
    HttpResponse<java.io.InputStream> response =
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);
    List<Sse> out = new ArrayList<>();
    int eventId = 0;
    String name = null;
    StringBuilder data = new StringBuilder();
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("id:")) {
          eventId = Integer.parseInt(line.substring(3).trim());
        } else if (line.startsWith("event:")) {
          name = line.substring(6).trim();
        } else if (line.startsWith("data:")) {
          data.append(line.substring(5).trim());
        } else if (line.isEmpty() && name != null) {
          out.add(new Sse(eventId, name, data.toString()));
          name = null;
          data.setLength(0);
        }
      }
    }
    return out;
  }

  protected static ByteArrayResource named(byte[] bytes, String filename) {
    return new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }
}
