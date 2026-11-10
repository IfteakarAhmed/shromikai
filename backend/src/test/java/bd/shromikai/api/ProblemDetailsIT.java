package bd.shromikai.api;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@IntegrationTest
@Requirement("FR-02")
@Requirement("FR-03")
class ProblemDetailsIT {

  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper mapper;
  @Autowired JwtService jwt;

  @Test
  void shouldReturnNotFoundProblem_whenRouteIsUnknown() throws Exception {
    ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/no-such-route", null, null);

    JsonNode problem = assertProblem(response, 404, "not-found");
    assertThat(problem.get("title").asText()).isEqualTo("পাওয়া যায়নি");
    assertThat(problem.get("instance").asText()).startsWith("/unmatched#");
  }

  @Test
  void shouldReturnValidationProblemWithPointers_whenBodyIsInvalid() throws Exception {
    ResponseEntity<String> response =
        exchange(
            HttpMethod.POST, "/api/v1/test-probe/validate", "{\"name\":\"\",\"count\":0}", null);

    JsonNode problem = assertProblem(response, 400, "validation-failed");
    assertThat(problem.get("instance").asText()).startsWith("/api/v1/test-probe/validate#");
    assertThat(problem.get("errors").findValuesAsText("pointer"))
        .containsExactlyInAnyOrder("/name", "/count");
  }

  @Test
  void shouldReturnValidationProblem_whenBodyIsNotJson() throws Exception {
    ResponseEntity<String> response =
        exchange(HttpMethod.POST, "/api/v1/test-probe/validate", "{not json", null);

    assertProblem(response, 400, "validation-failed");
  }

  @Test
  void shouldLocaliseTitle_whenClientPrefersEnglish() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-GB,en;q=0.9");

    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/api/v1/no-such-route", null, headers);

    assertThat(assertProblem(response, 404, "not-found").get("title").asText())
        .isEqualTo("Not found");
  }

  @Test
  void shouldEchoRequestIdAsTraceId_whenClientSendsOne() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Request-Id", "req-123");

    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/api/v1/no-such-route", null, headers);

    JsonNode problem = assertProblem(response, 404, "not-found");
    assertThat(problem.get("traceId").asText()).isEqualTo("req-123");
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
    assertThat(problem.get("instance").asText()).endsWith("#req-123");
  }

  @Test
  void shouldReturnMethodNotAllowed_whenMethodIsWrong() throws Exception {
    ResponseEntity<String> response = exchange(HttpMethod.DELETE, "/api/v1/meta", null, null);

    assertProblem(response, 405, "method-not-allowed");
  }

  @Test
  void shouldSetRetryAfter_whenProblemCarriesIt() throws Exception {
    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/api/v1/test-probe/problem/UPSTREAM_UNAVAILABLE", null, null);

    JsonNode problem = assertProblem(response, 503, "upstream-unavailable");
    assertThat(problem.get("retryAfterSeconds").asInt()).isEqualTo(30);
    assertThat(problem.get("instance").asText())
        .startsWith("/api/v1/test-probe/problem/%7Bcode%7D#");
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
  }

  @Test
  void shouldHideExceptionMessage_whenErrorIsUnexpected() throws Exception {
    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/api/v1/test-probe/boom", null, null);

    assertProblem(response, 500, "internal-error");
    assertThat(response.getBody()).doesNotContain("secret").doesNotContain("Exception");
  }

  @Test
  void shouldKeepFrameworkStatus_whenStatusHasNoSpecificCode() throws Exception {
    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/api/v1/test-probe/conflict", null, null);

    assertProblem(response, 409, "validation-failed");
  }

  @Test
  void shouldHideReasonCode_andPassRetryAfter_whenModelCallFails() throws Exception {
    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/api/v1/test-probe/model-failure", null, null);

    JsonNode problem = assertProblem(response, 503, "upstream-unavailable");
    assertThat(problem.path("detail").asText("")).isEmpty();
    assertThat(response.getBody()).doesNotContain("gemini").doesNotContain("http-503");
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
  }

  @Test
  void shouldKeepTheRequestId_whenAFilterThrows() throws Exception {
    ResponseEntity<String> response =
        exchange(HttpMethod.GET, ThrowingProbeFilter.PATH, null, null);

    JsonNode problem = assertProblem(response, 500, "internal-error");
    String header = response.getHeaders().getFirst("X-Request-Id");
    assertThat(header).isEqualTo(problem.get("traceId").asText());
    assertThat(ThrowingProbeFilter.lastRequestId()).isEqualTo(header);
    assertThat(problem.get("instance").asText()).endsWith("#" + header);
  }

  private ResponseEntity<String> exchange(
      HttpMethod method, String path, String body, HttpHeaders extra) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // Everything outside the public list needs a signed-in caller (FR-06); the problem documents
    // under test are those of a signed-in worker.
    headers.setBearerAuth(
        jwt.issueUser(java.util.UUID.randomUUID(), Role.WORKER, null, false).value());
    if (extra != null) {
      headers.addAll(extra);
    }
    return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
  }

  private JsonNode assertProblem(ResponseEntity<String> response, int status, String code)
      throws Exception {
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType())
        .isNotNull()
        .satisfies(
            type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
    JsonNode problem = mapper.readTree(response.getBody());
    assertThat(problem.get("type").asText()).isEqualTo("urn:shromikai:problem:" + code);
    assertThat(problem.get("status").asInt()).isEqualTo(status);
    assertThat(problem.get("code").asText()).isEqualTo(code);
    assertThat(problem.get("title").asText()).isNotBlank();
    assertThat(problem.get("instance").asText()).isNotBlank();
    assertThat(problem.get("traceId").asText()).isNotBlank();
    return problem;
  }
}
