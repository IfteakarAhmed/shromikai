package bd.shromikai.shared.ai.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.retry.support.RetryTemplate;

/** WireMock contract of the Ollama adapters (test-strategy.md §6.2). */
@Tag("it")
@Requirement("NFR-06")
class OllamaAdaptersIT {

  @RegisterExtension
  static WireMockExtension ollama =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private static final RetryTemplate NO_RETRY = RetryTemplate.builder().maxAttempts(1).build();

  private final ObjectMapper mapper = new ObjectMapper();

  private static OllamaApi api(String baseUrl) {
    return OllamaApi.builder().baseUrl(baseUrl).build();
  }

  private static OllamaChatModel chatModel(String baseUrl) {
    return OllamaChatModel.builder()
        .ollamaApi(api(baseUrl))
        .defaultOptions(OllamaChatOptions.builder().model("qwen2.5:7b-instruct").build())
        .toolCallingManager(ToolCallingManager.builder().build())
        .observationRegistry(ObservationRegistry.NOOP)
        .modelManagementOptions(ModelManagementOptions.defaults())
        .retryTemplate(NO_RETRY)
        .build();
  }

  private ChatRequest request() {
    return new ChatRequest(
        "xx-echo",
        1,
        ModelRole.LOCAL_PII,
        "qwen2.5:7b-instruct",
        "You echo the input as JSON.",
        SensitiveText.of("hello"),
        mapper.createObjectNode().put("type", "object"),
        64,
        Duration.ofSeconds(5),
        null,
        null);
  }

  @Test
  void shouldSendSchemaFormatAndTemperatureZero_toApiChat() throws Exception {
    ollama.stubFor(
        post("/api/chat")
            .willReturn(
                okJson(
                    """
                    {"model":"qwen2.5:7b-instruct","created_at":"2026-11-10T00:00:00Z",
                     "message":{"role":"assistant","content":"{\\"echo\\":\\"hello\\"}"},
                     "done":true,"done_reason":"stop","prompt_eval_count":9,"eval_count":4}
                    """)));
    OllamaChatAdapter adapter = new OllamaChatAdapter(chatModel(ollama.baseUrl()), mapper);

    ChatResponse response = adapter.call(request());

    assertThat(response.output().get("echo").asText()).isEqualTo("hello");
    assertThat(response.usage().inputTokens()).isEqualTo(9);
    JsonNode body =
        mapper.readTree(
            ollama.findAll(postRequestedFor(urlEqualTo("/api/chat"))).get(0).getBodyAsString());
    assertThat(body.get("model").asText()).isEqualTo("qwen2.5:7b-instruct");
    assertThat(body.at("/format/type").asText()).isEqualTo("object");
    assertThat(body.at("/options/temperature").asDouble()).isZero();
    assertThat(body.get("stream").asBoolean()).isFalse();
    assertThat(adapter.provider()).isEqualTo("ollama");
  }

  @Test
  void shouldFailClosed_whenOllamaIsUnreachable() {
    OllamaChatAdapter adapter = new OllamaChatAdapter(chatModel("http://localhost:1"), mapper);

    assertThatThrownBy(() -> adapter.call(request()))
        .isInstanceOf(LocalModelUnavailableException.class);
  }

  @Test
  void shouldEmbedInOneBatch_onApiEmbed() throws Exception {
    ollama.stubFor(
        post("/api/embed")
            .willReturn(okJson("{\"model\":\"bge-m3\",\"embeddings\":[[0.1,0.2],[0.3,0.4]]}")));
    OllamaEmbeddingModel model =
        OllamaEmbeddingModel.builder()
            .ollamaApi(api(ollama.baseUrl()))
            .defaultOptions(OllamaEmbeddingOptions.builder().model("bge-m3").build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    OllamaEmbeddingAdapter adapter = new OllamaEmbeddingAdapter(model, "bge-m3");

    List<float[]> vectors = adapter.embed(List.of("ধারা", "section"));

    assertThat(vectors).hasSize(2);
    assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);
    JsonNode body =
        mapper.readTree(
            ollama.findAll(postRequestedFor(urlEqualTo("/api/embed"))).get(0).getBodyAsString());
    assertThat(body.get("input")).hasSize(2);
    assertThat(body.get("model").asText()).isEqualTo("bge-m3");
    assertThat(adapter.embed(List.of())).isEmpty();
    assertThat(adapter.dimensions()).isEqualTo(1024);
    assertThat(adapter.modelId()).isEqualTo("bge-m3");
  }

  @Test
  void shouldFailClosed_whenEmbeddingIsUnreachable() {
    OllamaEmbeddingModel model =
        OllamaEmbeddingModel.builder()
            .ollamaApi(api("http://localhost:1"))
            .defaultOptions(OllamaEmbeddingOptions.builder().model("bge-m3").build())
            .build();

    assertThatThrownBy(() -> new OllamaEmbeddingAdapter(model, "bge-m3").embed(List.of("x")))
        .isInstanceOf(LocalModelUnavailableException.class);
  }
}
