package bd.shromikai.verify;

import static bd.shromikai.verify.VerifyFixtures.CLAIM_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.KEY_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.QUOTE_NOTICE;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.anthropic.AnthropicChatAdapter;
import bd.shromikai.shared.ai.gemini.GeminiChatAdapter;
import bd.shromikai.shared.ai.internal.Backoff;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.VerifyFixtures.Corpus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * FR-44: the support check against WireMock stand-ins for Gemini and Claude. The stubbed bodies
 * have the providers' response shapes; they are hand-written, not recorded from a live call (tests
 * make no cloud call). Covers every verdict, malformed output and the Gemini outage fallback.
 */
@Tag("unit")
@Requirement("FR-44")
class SupportCheckContractTest {

  private static final String GEMINI_PATH = "/v1beta/models/gemini-test:generateContent";

  @RegisterExtension
  static WireMockExtension gemini =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @RegisterExtension
  static WireMockExtension anthropic =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void reset() {
    gemini.resetAll();
    anthropic.resetAll();
  }

  private CitationVerifier verifier(boolean withGemini) {
    ShromikaiProperties.Models models = VerifyFixtures.models(withGemini ? "gemini-test" : null);
    var geminiAdapter =
        new GeminiChatAdapter(
            request -> {},
            new ShromikaiProperties.Provider("gemini-key", gemini.baseUrl()),
            mapper,
            new Backoff(Duration.ZERO, millis -> {}));
    var claudeAdapter =
        new AnthropicChatAdapter(
            request -> {},
            new ShromikaiProperties.Provider("claude-key", anthropic.baseUrl()),
            mapper);
    ModelGateway gateway = VerifyFixtures.gateway(models, geminiAdapter, claudeAdapter);
    return VerifyFixtures.verifier(new Corpus(), gateway, models);
  }

  private static String geminiBody(String text) {
    return """
        {"candidates":[{"content":{"parts":[{"text":%s}],"role":"model"},"finishReason":"STOP"}],
         "usageMetadata":{"promptTokenCount":300,"candidatesTokenCount":30},
         "modelVersion":"gemini-test"}
        """
        .formatted(new ObjectMapper().valueToTree(text).toString());
  }

  private static String claudeBody(String text) {
    return """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
         "content":[{"type":"text","text":%s}],"stop_reason":"end_turn","stop_sequence":null,
         "usage":{"input_tokens":300,"output_tokens":30,"cache_read_input_tokens":0}}
        """
        .formatted(new ObjectMapper().valueToTree(text).toString());
  }

  private Verdict verify(CitationVerifier verifier) {
    return verifier.verify(
        VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
        VerifyFixtures.context(KEY_NOTICE));
  }

  @ParameterizedTest(name = "{0} at {1} gives {2}")
  @CsvSource({
    "SUPPORTS, 0.95, VERIFIED",
    "SUPPORTS, 0.50, UNCERTAIN",
    "PARTIAL, 0.90, UNCERTAIN",
    "CONTRADICTS, 0.90, REJECTED",
    "UNRELATED, 0.90, REJECTED"
  })
  void shouldMapEachGeminiVerdictToTheDecisionTableStatus(
      String label, double confidence, VerificationStatus expected) {
    gemini.stubFor(
        post(urlPathEqualTo(GEMINI_PATH))
            .willReturn(
                okJson(
                    geminiBody(
                        "{\"label\":\""
                            + label
                            + "\",\"confidence\":"
                            + confidence
                            + ",\"reason\":\"recorded stand-in\"}"))));

    Verdict verdict = verify(verifier(true));

    assertThat(verdict.result().status()).isEqualTo(expected);
    assertThat(verdict.result().checks().support().label()).isEqualTo(SupportLabel.valueOf(label));
    assertThat(verdict.result().checks().support().independence())
        .isEqualTo(VerifierIndependence.DIFFERENT_FAMILY);
  }

  @Test
  void shouldCountMalformedOutputAsUnrelated() {
    gemini.stubFor(post(urlPathEqualTo(GEMINI_PATH)).willReturn(okJson(geminiBody("not json"))));

    Verdict verdict = verify(verifier(true));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(verdict.result().checks().support().label()).isEqualTo(SupportLabel.UNRELATED);
    assertThat(verdict.result().reasonCodes()).containsExactly(ReasonCode.SUPPORT_UNRELATED);
    gemini.verify(2, postRequestedFor(urlPathEqualTo(GEMINI_PATH))); // one call plus one repair
  }

  @Test
  void shouldCountSchemaInvalidJsonAsUnrelated() {
    gemini.stubFor(
        post(urlPathEqualTo(GEMINI_PATH)).willReturn(okJson(geminiBody("{\"verdict\":\"yes\"}"))));

    Verdict verdict = verify(verifier(true));

    assertThat(verdict.result().checks().support().label()).isEqualTo(SupportLabel.UNRELATED);
    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.REJECTED);
  }

  @Test
  void shouldFallBackToClaudeWithSameFamily_whenGeminiAnswers503() {
    gemini.stubFor(post(urlPathEqualTo(GEMINI_PATH)).willReturn(aResponse().withStatus(503)));
    anthropic.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                okJson(
                    claudeBody(
                        "{\"label\":\"SUPPORTS\",\"confidence\":0.9,\"reason\":\"fallback\"}"))));

    Verdict verdict = verify(verifier(true));

    assertThat(verdict.verified()).isTrue();
    assertThat(verdict.result().checks().support().independence())
        .isEqualTo(VerifierIndependence.SAME_FAMILY);
    gemini.verify(3, postRequestedFor(urlPathEqualTo(GEMINI_PATH))); // 1 call + 2 retries
    anthropic.verify(1, postRequestedFor(urlEqualTo("/v1/messages")));
  }

  @Test
  void shouldUseClaudeDirectly_whenNoGeminiModelIsConfigured() {
    anthropic.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                okJson(
                    claudeBody("{\"label\":\"PARTIAL\",\"confidence\":0.8,\"reason\":\"part\"}"))));

    Verdict verdict = verify(verifier(false));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.UNCERTAIN);
    assertThat(verdict.result().checks().support().independence())
        .isEqualTo(VerifierIndependence.SAME_FAMILY);
    gemini.verify(0, postRequestedFor(urlPathEqualTo(GEMINI_PATH)));
  }

  @Test
  void shouldBeUncertainWhenBothVerifiersAreDown() {
    gemini.stubFor(post(urlPathEqualTo(GEMINI_PATH)).willReturn(aResponse().withStatus(500)));
    anthropic.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(500)));

    Verdict verdict = verify(verifier(true));

    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.UNCERTAIN);
    assertThat(verdict.result().reasonCodes()).containsExactly(ReasonCode.VERIFIER_UNAVAILABLE);
    assertThat(verdict.citation()).isNull();
  }

  @Test
  void shouldKeepTheKeyOutOfTheUrlAndSendTemperatureZeroToGemini() throws Exception {
    gemini.stubFor(
        post(urlPathEqualTo(GEMINI_PATH))
            .willReturn(
                okJson(
                    geminiBody("{\"label\":\"SUPPORTS\",\"confidence\":0.9,\"reason\":\"ok\"}"))));

    verify(verifier(true));

    var request = gemini.getAllServeEvents().getFirst().getRequest();
    assertThat(request.getUrl()).doesNotContain("gemini-key");
    assertThat(request.getHeader("x-goog-api-key")).isEqualTo("gemini-key");
    assertThat(
            mapper
                .readTree(request.getBodyAsString())
                .at("/generationConfig/temperature")
                .asDouble())
        .isZero();
  }
}
