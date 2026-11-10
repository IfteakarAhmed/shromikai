package bd.shromikai.shared.ai.gemini;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.ai.internal.Backoff;
import bd.shromikai.shared.ai.internal.FailClosedEgressGuard;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** WireMock contract of the Gemini adapter (test-strategy.md §6.2, ADR-0005). */
@Tag("it")
@Requirement("NFR-06")
class GeminiChatAdapterIT {

  private static final String PATH = "/v1beta/models/gemini-test:generateContent";

  @RegisterExtension
  static WireMockExtension gemini =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private final ObjectMapper mapper = new ObjectMapper();
  private final GeminiChatAdapter adapter =
      new GeminiChatAdapter(
          request -> {},
          new ShromikaiProperties.Provider("gemini-key", gemini.baseUrl()),
          mapper,
          new Backoff(Duration.ZERO, millis -> {}));

  private ChatRequest request() {
    return new ChatRequest(
        "xx-echo",
        1,
        ModelRole.VERIFIER,
        "gemini-test",
        "You echo the input as JSON.",
        SensitiveText.of("hello"),
        mapper.createObjectNode().put("type", "object"),
        64,
        Duration.ofSeconds(5),
        null,
        null);
  }

  private static String answer(String finishReason, String text) {
    return """
        {"candidates":[{"content":{"parts":[{"text":%s}]},"finishReason":"%s"}],
         "usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3},"modelVersion":"gemini-test-001"}
        """
        .formatted(new ObjectMapper().valueToTree(text).toString(), finishReason);
  }

  @Test
  void shouldUseJsonModeTemperatureZeroAndKeyHeader() throws Exception {
    gemini.stubFor(
        post(urlPathEqualTo(PATH)).willReturn(okJson(answer("STOP", "{\"echo\":\"hello\"}"))));

    ChatResponse response = adapter.call(request());

    assertThat(response.output().get("echo").asText()).isEqualTo("hello");
    assertThat(response.modelId()).isEqualTo("gemini-test-001");
    assertThat(response.usage().inputTokens()).isEqualTo(7);
    LoggedRequest sent = gemini.findAll(postRequestedFor(urlPathEqualTo(PATH))).get(0);
    assertThat(sent.getUrl()).doesNotContain("key=").doesNotContain("gemini-key");
    assertThat(sent.getHeader("x-goog-api-key")).isEqualTo("gemini-key");
    JsonNode body = mapper.readTree(sent.getBodyAsString());
    assertThat(body.at("/generationConfig/responseMimeType").asText())
        .isEqualTo("application/json");
    assertThat(body.at("/generationConfig/responseJsonSchema/type").asText()).isEqualTo("object");
    assertThat(body.at("/generationConfig/temperature").asDouble()).isZero();
    assertThat(body.at("/contents/0/parts/0/text").asText()).isEqualTo("hello");
    assertThat(adapter.provider()).isEqualTo("gemini");
  }

  @Test
  void shouldRetryTwice_whenServiceIsUnavailable() {
    gemini.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> adapter.call(request()))
        .isInstanceOf(UpstreamUnavailableException.class);
    gemini.verify(
        3,
        postRequestedFor(urlPathEqualTo(PATH)).withHeader("x-goog-api-key", equalTo("gemini-key")));
  }

  @Test
  void shouldMapSafetyStop_toRefusal_andBadJson_toInvalidOutput() {
    gemini.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(answer("SAFETY", ""))));
    assertThatThrownBy(() -> adapter.call(request())).isInstanceOf(ModelRefusedException.class);

    gemini.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(answer("STOP", "not json"))));
    assertThatThrownBy(() -> adapter.call(request()))
        .isInstanceOf(ModelOutputInvalidException.class);
  }

  @Test
  void shouldSendNothing_whenGuardBlocksOrKeyIsMissing() {
    GeminiChatAdapter blocked =
        new GeminiChatAdapter(
            new FailClosedEgressGuard(),
            new ShromikaiProperties.Provider("k", gemini.baseUrl()),
            mapper,
            new Backoff());
    GeminiChatAdapter unconfigured =
        new GeminiChatAdapter(
            request -> {},
            new ShromikaiProperties.Provider(null, gemini.baseUrl()),
            mapper,
            new Backoff());

    assertThatThrownBy(() -> blocked.call(request())).isInstanceOf(EgressBlockedException.class);
    assertThatThrownBy(() -> unconfigured.call(request()))
        .isInstanceOf(UpstreamUnavailableException.class);
    gemini.verify(0, postRequestedFor(urlPathEqualTo(PATH)));
  }
}
