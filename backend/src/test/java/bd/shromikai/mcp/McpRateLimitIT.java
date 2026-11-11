package bd.shromikai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** FR-65, T-12: 60 calls per minute per key; the 61st is refused with a tool error. */
@Requirement("FR-65")
class McpRateLimitIT extends McpIT {

  private JsonNode call(String key, String sessionId) throws Exception {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setBearerAuth(key);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    headers.setAccept(
        java.util.List.of(
            org.springframework.http.MediaType.APPLICATION_JSON,
            org.springframework.http.MediaType.TEXT_EVENT_STREAM));
    headers.set("Mcp-Session-Id", sessionId);
    var response =
        http.exchange(
            "/mcp",
            org.springframework.http.HttpMethod.POST,
            new org.springframework.http.HttpEntity<>(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    2,
                    "method",
                    "tools/call",
                    "params",
                    Map.of("name", "list_jurisdiction_packs", "arguments", Map.of())),
                headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    int data = body.indexOf("data:");
    return mapper.readTree(data >= 0 ? body.substring(data + 5).trim() : body).path("result");
  }

  private String session(String key) {
    return initialize(key).getHeaders().getFirst("Mcp-Session-Id");
  }

  @Test
  void the61stCallInAMinuteIsRefusedAndTheLimitIsPerKey() throws Exception {
    String first = key("mcp");
    String second = key("mcp");
    String firstSession = session(first);
    String secondSession = session(second);

    for (int i = 0; i < 60; i++) {
      assertThat(call(first, firstSession).path("isError").asBoolean(false))
          .as("call " + (i + 1))
          .isFalse();
    }
    JsonNode refused = call(first, firstSession);

    assertThat(refused.path("isError").asBoolean()).isTrue();
    assertThat(refused.path("structuredContent").path("code").asText()).isEqualTo("rate-limited");
    assertThat(refused.path("structuredContent").path("retryAfterSeconds").asInt()).isPositive();
    assertThat(call(second, secondSession).path("isError").asBoolean(false))
        .as("another key is unaffected")
        .isFalse();
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'ACCESS_DENIED'"
                        + " and detail->>'reason' = 'rate_limited'")
                .query(Long.class)
                .single())
        .isEqualTo(1L);
    assertThat(
            jdbc.sql("select count(*) from usage_event where kind = 'MCP_CALL'")
                .query(Long.class)
                .single())
        .as("a refused call is not metered")
        .isEqualTo(61L);
  }
}
