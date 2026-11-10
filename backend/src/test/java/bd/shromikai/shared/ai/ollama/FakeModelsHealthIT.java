package bd.shromikai.shared.ai.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Under {@code fake-models} nothing calls Ollama, so its health component is absent and an
 * unreachable Ollama cannot turn health DOWN (FR-04, architecture §16.3).
 */
@IntegrationTest
@ActiveProfiles({"test", "fake-models"})
@Requirement("FR-04")
class FakeModelsHealthIT {

  @Autowired TestRestTemplate http;
  @Autowired ApplicationContext context;

  @Test
  void shouldOmitOllamaHealth_whenModelsAreFaked() {
    JsonNode health = http.getForObject("/actuator/health", JsonNode.class);

    assertThat(context.getBeanNamesForType(OllamaHealthIndicator.class)).isEmpty();
    assertThat(health.at("/components/ollama").isMissingNode()).isTrue();
    assertThat(health.at("/components/db/status").asText()).isEqualTo("UP");
    assertThat(health.at("/status").asText()).isEqualTo("UP");
  }
}
