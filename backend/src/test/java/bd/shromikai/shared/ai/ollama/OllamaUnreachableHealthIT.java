package bd.shromikai.shared.ai.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * CI has no Ollama. The public liveness check {@code /actuator/health} must stay 200 and Ollama
 * must be reported only in the {@code dependencies} group (FR-04).
 */
@IntegrationTest
@Requirement("FR-04")
class OllamaUnreachableHealthIT {

  @DynamicPropertySource
  static void unreachableOllama(DynamicPropertyRegistry registry) {
    registry.add("shromikai.ollama.base-url", () -> "http://127.0.0.1:1");
  }

  @Autowired TestRestTemplate http;

  @Test
  void shouldKeepPublicHealthUp_whenOllamaIsUnreachable() {
    ResponseEntity<JsonNode> response = http.getForEntity("/actuator/health", JsonNode.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().at("/status").asText()).isEqualTo("UP");
    assertThat(response.getBody().at("/components/ollama").isMissingNode()).isTrue();
  }

  @Test
  void shouldReportOllamaDown_inTheDependenciesGroup() {
    ResponseEntity<JsonNode> response =
        http.getForEntity("/actuator/health/dependencies", JsonNode.class);

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody().at("/components/ollama/status").asText()).isEqualTo("DOWN");
    assertThat(response.getBody().at("/components/db/status").asText()).isEqualTo("UP");
  }
}
