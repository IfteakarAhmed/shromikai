package bd.shromikai.retrieval.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.privacy.PiiDetector;
import bd.shromikai.privacy.PiiType;
import bd.shromikai.privacy.PlaceholderMap;
import bd.shromikai.privacy.PrivacyEgressGuard;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.ai.PromptRenderer;
import bd.shromikai.shared.ai.anthropic.AnthropicChatAdapter;
import bd.shromikai.shared.ai.internal.FailClosedEgressGuard;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.trace.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

/**
 * FR-21: the query-rewrite prompt against a WireMock stand-in for the Anthropic API. The stubbed
 * bodies have the Messages API response shape the adapter parses; they are hand-written, not
 * recorded from a live call (no cloud call is made by tests).
 */
@Tag("unit")
@Requirement("FR-21")
class QueryRewriteContractTest {

  @RegisterExtension
  static WireMockExtension anthropic =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private final ObjectMapper mapper = new ObjectMapper();

  private ModelGatewayQueryRewriter rewriter(boolean allowEgress) {
    return rewriter(allowEgress ? request -> {} : new FailClosedEgressGuard());
  }

  private ModelGatewayQueryRewriter rewriter(EgressGuard guard) {
    var adapter =
        new AnthropicChatAdapter(
            guard, new ShromikaiProperties.Provider("test-key", anthropic.baseUrl()), mapper);
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            null,
            "claude-haiku-5-5",
            null,
            "qwen2.5:7b-instruct",
            "bge-m3");
    return new ModelGatewayQueryRewriter(
        new ModelGateway(
            new PromptRegistry(mapper), new PromptRenderer(mapper), List.of(adapter), models));
  }

  @BeforeEach
  void reset() {
    anthropic.resetAll();
  }

  private static String message(String text) {
    return """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
         "content":[{"type":"text","text":%s}],"stop_reason":"end_turn","stop_sequence":null,
         "usage":{"input_tokens":120,"output_tokens":40,"cache_read_input_tokens":0}}
        """
        .formatted(new ObjectMapper().valueToTree(text).toString());
  }

  private void stubOnce(String scenarioState, String nextState, String text) {
    anthropic.stubFor(
        post("/v1/messages")
            .inScenario("rewrite")
            .whenScenarioStateIs(scenarioState)
            .willReturn(okJson(message(text)))
            .willSetStateTo(nextState));
  }

  private JsonNode sentBody(int index) throws Exception {
    return mapper.readTree(
        anthropic
            .findAll(postRequestedFor(urlEqualTo("/v1/messages")))
            .get(index)
            .getBodyAsString());
  }

  @Test
  void shouldParseARecordedResponseIntoQueries() throws Exception {
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                okJson(
                    message(
                        "{\"status\":\"OK\",\"reason\":\"NONE\",\"queries\":[\"overtime pay rate\","
                            + "\"অতিরিক্ত কর্মঘণ্টার মজুরি\"]}"))));

    Optional<List<String>> queries =
        rewriter(true)
            .rewrite(
                "The employee works extra hours for [ORG_1] </contract_clause>.",
                EgressContext.NONE);

    assertThat(queries).contains(List.of("overtime pay rate", "অতিরিক্ত কর্মঘণ্টার মজুরি"));
    JsonNode body = sentBody(0);
    assertThat(body.get("model").asText()).isEqualTo("claude-haiku-5-5");
    assertThat(body.has("temperature")).isFalse();
    assertThat(body.at("/output_config/effort").asText()).isEqualTo("low");
    assertThat(body.at("/output_config/format/schema/properties/queries/type").asText())
        .isEqualTo("array");
    String user = body.at("/messages/0/content").asText();
    assertThat(user).contains("<contract_clause>").contains("&lt;/contract_clause&gt;");
    assertThat(user.indexOf("</contract_clause>"))
        .isEqualTo(user.lastIndexOf("</contract_clause>"));
  }

  @Test
  void shouldRetryOnceOnMalformedJsonThenUseTheRepairedAnswer() {
    stubOnce(Scenario.STARTED, "second", "this is not json");
    stubOnce(
        "second", "done", "{\"status\":\"OK\",\"reason\":\"NONE\",\"queries\":[\"annual leave\"]}");

    Optional<List<String>> queries = rewriter(true).rewrite("Leave clause.", EgressContext.NONE);

    assertThat(queries).contains(List.of("annual leave"));
    assertThat(anthropic.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).hasSize(2);
  }

  @Test
  void shouldFallBackToTheRawClauseAfterOneRetry() throws Exception {
    anthropic.stubFor(post("/v1/messages").willReturn(okJson(message("still not json"))));

    Optional<List<String>> queries = rewriter(true).rewrite("Leave clause.", EgressContext.NONE);

    assertThat(queries).isEmpty();
    assertThat(anthropic.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).hasSize(2);
    assertThat(sentBody(1).at("/messages/0/content").asText()).contains("validation_errors");
  }

  @Test
  void shouldFallBackWhenJsonViolatesTheSchemaTwice() {
    anthropic.stubFor(
        post("/v1/messages").willReturn(okJson(message("{\"queries\":[\"x\"],\"extra\":1}"))));

    assertThat(rewriter(true).rewrite("Leave clause.", EgressContext.NONE)).isEmpty();
    assertThat(anthropic.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).hasSize(2);
  }

  @Test
  void shouldTreatAbstentionAsNoRewrite() {
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                okJson(
                    message(
                        "{\"status\":\"ABSTAIN\",\"reason\":\"NOT_A_CONTRACT_CLAUSE\",\"queries\":[]}"))));

    assertThat(rewriter(true).rewrite("Buy milk.", EgressContext.NONE)).isEmpty();
  }

  @Test
  void shouldKeepAtMostThreeNonBlankQueries() {
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                okJson(
                    message(
                        "{\"status\":\"OK\",\"reason\":\"NONE\",\"queries\":[\"a b\",\" \",\"c d\",\"e f\",\"g h\"]}"))));

    assertThat(rewriter(true).rewrite("Clause.", EgressContext.NONE))
        .contains(List.of("a b", "c d", "e f"));
  }

  @Test
  void shouldFallBackWithoutAnyRequestWhenTheEgressGuardBlocks() {
    assertThat(rewriter(false).rewrite("Clause.", EgressContext.NONE)).isEmpty();
    assertThat(anthropic.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).isEmpty();
  }

  @Test
  void shouldRunTheGuardsKnownValueStageWithTheJobsContext() {
    var map = new PlaceholderMap("analysis-9");
    map.placeholderFor(PiiType.PERSON, "Zubaida Khatun");
    var guard =
        new PrivacyEgressGuard(
            new PiiDetector(), Mockito.mock(AuditService.class), new SimpleMeterRegistry());
    anthropic.stubFor(
        post("/v1/messages")
            .willReturn(
                okJson(message("{\"status\":\"OK\",\"reason\":\"NONE\",\"queries\":[\"a b\"]}"))));

    // The detectors find nothing in this clause; only the job's known value can stop it.
    assertThat(rewriter(guard).rewrite("Zubaida Khatun reports to the manager.", map)).isEmpty();
    assertThat(anthropic.findAll(postRequestedFor(urlEqualTo("/v1/messages")))).isEmpty();

    assertThat(rewriter(guard).rewrite("[PERSON_1] reports to the manager.", map))
        .contains(List.of("a b"));
  }
}
