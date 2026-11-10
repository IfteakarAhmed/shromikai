package bd.shromikai.shared.ai.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@IntegrationTest
@Requirement("FR-04")
class HealthIndicatorsIT {

  @RegisterExtension
  static WireMockExtension ollama =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @DynamicPropertySource
  static void ollamaUrl(DynamicPropertyRegistry registry) {
    registry.add("shromikai.ollama.base-url", ollama::baseUrl);
  }

  @Autowired TestRestTemplate http;

  @Test
  void shouldReportDbAndOllamaUp_whenOllamaHasBothModels() {
    stubOllama("{\"models\":[{\"name\":\"bge-m3:latest\"},{\"name\":\"qwen2.5:7b-instruct\"}]}");

    JsonNode health = http.getForObject("/actuator/health", JsonNode.class);

    assertThat(health.at("/components/db/status").asText()).isEqualTo("UP");
    assertThat(health.at("/components/ollama/status").asText()).isEqualTo("UP");
    assertThat(health.at("/status").asText()).isEqualTo("UP");
  }

  @Test
  void shouldReportOllamaDown_whenOllamaFails() {
    ollama.stubFor(get("/api/version").willReturn(aResponse().withStatus(500)));

    JsonNode health = http.getForObject("/actuator/health", JsonNode.class);

    assertThat(health.at("/components/db/status").asText()).isEqualTo("UP");
    assertThat(health.at("/components/ollama/status").asText()).isEqualTo("DOWN");
    assertThat(health.at("/status").asText()).isEqualTo("DOWN");
  }

  @Test
  void shouldReportOllamaDown_whenAModelIsMissing() {
    stubOllama("{\"models\":[{\"name\":\"bge-m3:latest\"}]}");

    JsonNode health = http.getForObject("/actuator/health", JsonNode.class);

    assertThat(health.at("/components/ollama/status").asText()).isEqualTo("DOWN");
  }

  private static void stubOllama(String tags) {
    ollama.stubFor(get("/api/version").willReturn(okJson("{\"version\":\"0.40.16\"}")));
    ollama.stubFor(get("/api/tags").willReturn(okJson(tags)));
  }
}
