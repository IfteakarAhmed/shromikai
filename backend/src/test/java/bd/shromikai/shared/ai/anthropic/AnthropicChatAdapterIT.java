package bd.shromikai.shared.ai.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import bd.shromikai.shared.ai.Effort;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRequestRejectedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.ai.internal.FailClosedEgressGuard;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** WireMock contract of the Claude adapter (test-strategy.md §6.2, ADR-0005). */
@Tag("it")
@Requirement("NFR-06")
class AnthropicChatAdapterIT {

  @RegisterExtension
  static WireMockExtension anthropic =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<String> guarded = new ArrayList<>();
  private final EgressGuard recordingGuard = request -> guarded.add(request.promptId());

  private AnthropicChatAdapter adapter(EgressGuard guard) {
    return new AnthropicChatAdapter(
        guard, new ShromikaiProperties.Provider("test-key", anthropic.baseUrl()), mapper);
  }

  private ChatRequest request(ModelRole role, Effort effort) throws Exception {
    return new ChatRequest(
        "xx-echo",
        1,
        role,
        "claude-haiku-5-5",
        "You echo the input as JSON.",
        SensitiveText.of("hello"),
        mapper.readTree(
            "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\","
                + "\"properties\":{\"echo\":{\"type\":\"string\"}},\"required\":[\"echo\"],"
                + "\"additionalProperties\":false}"),
        200,
        Duration.ofSeconds(5),
        null,
        effort);
  }

  private static String message(String stopReason, String text) {
    return """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
         "content":[{"type":"text","text":%s}],"stop_reason":"%s","stop_sequence":null,
         "usage":{"input_tokens":10,"output_tokens":3,"cache_read_input_tokens":2}}
        """
        .formatted(new ObjectMapper().valueToTree(text).toString(), stopReason);
  }

  @Test
  void shouldSendStructuredOutputWithoutSamplingParameters() throws Exception {
    anthropic.stubFor(
        post("/v1/messages").willReturn(okJson(message("end_turn", "{\"echo\":\"hello\"}"))));

    ChatResponse response = adapter(recordingGuard).call(request(ModelRole.FAST, Effort.LOW));

    assertThat(response.output().get("echo").asText()).isEqualTo("hello");
    assertThat(response.usage().cacheReadTokens()).isEqualTo(2);
    assertThat(response.recorded()).isFalse();
    assertThat(guarded).containsExactly("xx-echo");
    JsonNode body = sentBody();
    assertThat(body.get("model").asText()).isEqualTo("claude-haiku-5-5");
    assertThat(body.get("max_tokens").asInt()).isEqualTo(200);
    assertThat(body.at("/output_config/format/type").asText()).isEqualTo("json_schema");
    assertThat(body.at("/output_config/format/schema/properties/echo/type").asText())
        .isEqualTo("string");
    assertThat(body.at("/output_config/format/schema").has("$schema")).isFalse();
    assertThat(body.at("/output_config/effort").asText()).isEqualTo("low");
    assertThat(body.has("temperature")).isFalse();
    assertThat(body.has("top_p")).isFalse();
    assertThat(body.has("top_k")).isFalse();
    assertThat(body.has("fallbacks")).isFalse();
    anthropic.verify(
        postRequestedFor(urlEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("test-key"))
            .withoutHeader("Authorization"));
  }

  @Test
  void shouldRequestRefusalFallback_whenRoleIsReasoning() throws Exception {
    anthropic.stubFor(
        post("/v1/messages").willReturn(okJson(message("end_turn", "{\"echo\":\"x\"}"))));

    adapter(recordingGuard).call(request(ModelRole.REASONING, Effort.MEDIUM));

    assertThat(sentBody().get("fallbacks").asText()).isEqualTo("default");
    anthropic.verify(
        postRequestedFor(urlEqualTo("/v1/messages"))
            .withHeader("anthropic-beta", equalTo(AnthropicChatAdapter.FALLBACK_BETA)));
  }

  @Test
  void shouldMapRefusal_toModelRefusedException() {
    anthropic.stubFor(post("/v1/messages").willReturn(okJson(message("refusal", ""))));

    assertThatThrownBy(() -> adapter(recordingGuard).call(request(ModelRole.FAST, null)))
        .isInstanceOf(ModelRefusedException.class);
  }

  @Test
  void shouldReportInvalidOutput_whenTextIsNotJson() {
    anthropic.stubFor(post("/v1/messages").willReturn(okJson(message("end_turn", "not json"))));

    assertThatThrownBy(() -> adapter(recordingGuard).call(request(ModelRole.FAST, null)))
        .isInstanceOf(ModelOutputInvalidException.class);
  }

  @Test
  void shouldRetryTwice_thenReportUpstreamUnavailable() {
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                aResponse()
                    .withStatus(529)
                    .withHeader("retry-after-ms", "10")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"x\"}}")));

    assertThatThrownBy(() -> adapter(recordingGuard).call(request(ModelRole.FAST, null)))
        .isInstanceOfSatisfying(
            UpstreamUnavailableException.class, e -> assertThat(e.httpStatus()).isEqualTo(529));
    anthropic.verify(3, postRequestedFor(urlEqualTo("/v1/messages")));
  }

  @Test
  void shouldMapClientError_toInternalErrorWithoutRetry() {
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"x\"}}")));

    assertThatThrownBy(() -> adapter(recordingGuard).call(request(ModelRole.FAST, null)))
        .isInstanceOfSatisfying(
            ModelRequestRejectedException.class,
            e -> {
              assertThat(e.type()).isEqualTo(ProblemType.INTERNAL_ERROR);
              assertThat(e.httpStatus()).isEqualTo(400);
              assertThat(e.detail()).isNull();
            });
    anthropic.verify(1, postRequestedFor(urlEqualTo("/v1/messages")));
  }

  @Test
  void shouldPassRetryAfterThrough_whenRateLimited() {
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("retry-after", "7")
                    .withHeader("retry-after-ms", "10")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"x\"}}")));

    assertThatThrownBy(() -> adapter(recordingGuard).call(request(ModelRole.FAST, null)))
        .isInstanceOfSatisfying(
            UpstreamUnavailableException.class,
            e -> {
              assertThat(e.httpStatus()).isEqualTo(429);
              assertThat(e.retryAfterSeconds()).isEqualTo(7);
              assertThat(e.detail()).isNull();
            });
  }

  @Test
  void shouldSendNothing_whenEgressGuardBlocks() {
    assertThatThrownBy(
            () -> adapter(new FailClosedEgressGuard()).call(request(ModelRole.FAST, null)))
        .isInstanceOf(EgressBlockedException.class);
    anthropic.verify(0, postRequestedFor(urlEqualTo("/v1/messages")));
  }

  @Test
  void shouldRejectTemperatureAndMissingKey() throws Exception {
    ChatRequest base = request(ModelRole.FAST, null);
    ChatRequest withTemperature =
        new ChatRequest(
            base.promptId(),
            1,
            base.role(),
            base.modelId(),
            base.system(),
            base.user(),
            base.outputSchema(),
            10,
            base.timeout(),
            0.0,
            null);
    AnthropicChatAdapter unconfigured =
        new AnthropicChatAdapter(
            recordingGuard, new ShromikaiProperties.Provider("", anthropic.baseUrl()), mapper);

    assertThatThrownBy(() -> adapter(recordingGuard).call(withTemperature))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> unconfigured.call(base))
        .isInstanceOfSatisfying(
            UpstreamUnavailableException.class,
            e -> assertThat(e.httpStatus()).isEqualTo(UpstreamUnavailableException.NOT_CONFIGURED));
    assertThat(unconfigured.provider()).isEqualTo("anthropic");
  }

  private JsonNode sentBody() throws Exception {
    return mapper.readTree(
        anthropic.findAll(postRequestedFor(urlEqualTo("/v1/messages"))).get(0).getBodyAsString());
  }
}
