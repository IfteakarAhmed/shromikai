package bd.shromikai.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** C29 and C30: dynamic enums are enforced by the gateway; usage reaches the listener. */
@Tag("unit")
@Requirement("FR-37")
@Requirement("FR-48")
class ModelGatewayDynamicEnumTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PATH = "$.keys[*]";

  private Prompt prompt() throws Exception {
    JsonNode schema =
        MAPPER.readTree(
            """
            {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
             "additionalProperties":false,"required":["keys"],
             "properties":{"keys":{"type":"array","items":{"type":"string"}}}}
            """);
    return new Prompt(
        "xx-enum",
        1,
        "experimental",
        ModelRole.FAST,
        null,
        Effort.LOW,
        100,
        5,
        List.of(new Prompt.Variable("candidates", true, false)),
        "schemas/xx.json",
        schema,
        "System.",
        "<task_context>{{candidates}}</task_context>",
        List.of(new DynamicEnum(PATH, "candidates")));
  }

  private static ModelGateway gateway(
      Map<Integer, String> answers, AtomicInteger calls, List<LlmCall> seen) {
    ChatPort port =
        new ChatPort() {
          @Override
          public String provider() {
            return "anthropic";
          }

          @Override
          public ChatResponse call(ChatRequest request) {
            int n = calls.incrementAndGet();
            return new ChatResponse(
                TestGateways.json(answers.get(n)),
                "claude-haiku-5-5",
                new TokenUsage(100, 10, 0, 0),
                false);
          }
        };
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            null,
            "claude-haiku-5-5",
            null,
            "qwen2.5:7b-instruct",
            "bge-m3");
    return new ModelGateway(
        new PromptRegistry(MAPPER),
        new PromptRenderer(MAPPER),
        List.of(port),
        models,
        List.of(seen::add));
  }

  @Test
  void shouldAcceptAnAnswerInsideTheEnumAndReportUsage() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    List<LlmCall> seen = new ArrayList<>();
    ModelGateway gateway = gateway(Map.of(1, "{\"keys\":[\"XA:1@original\"]}"), calls, seen);

    ModelGateway.Result result =
        gateway.call(
            prompt(),
            Map.of("candidates", List.of("XA:1@original")),
            null,
            Map.of(PATH, List.of("XA:1@original")));

    assertThat(result.output().path("keys").get(0).asText()).isEqualTo("XA:1@original");
    assertThat(result.usage().inputTokens()).isEqualTo(100);
    assertThat(seen).hasSize(1);
    assertThat(seen.getFirst().promptId()).isEqualTo("xx-enum");
  }

  @Test
  void shouldRepairOnceAndSumTheUsage_whenTheFirstAnswerLeavesTheEnum() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    List<LlmCall> seen = new ArrayList<>();
    ModelGateway gateway =
        gateway(
            Map.of(1, "{\"keys\":[\"XA:999@original\"]}", 2, "{\"keys\":[\"XA:1@original\"]}"),
            calls,
            seen);

    ModelGateway.Result result =
        gateway.call(
            prompt(),
            Map.of("candidates", List.of("XA:1@original")),
            null,
            Map.of(PATH, List.of("XA:1@original")));

    assertThat(calls).hasValue(2);
    assertThat(result.usage().inputTokens()).isEqualTo(200);
    assertThat(seen).hasSize(1);
  }

  @Test
  void shouldFailWithoutAThirdCall_whenTheRepairAlsoLeavesTheEnum() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    List<LlmCall> seen = new ArrayList<>();
    ModelGateway gateway =
        gateway(
            Map.of(1, "{\"keys\":[\"XA:999@original\"]}", 2, "{\"keys\":[\"XA:998@original\"]}"),
            calls,
            seen);

    assertThatThrownBy(
            () ->
                gateway.call(
                    prompt(),
                    Map.of("candidates", List.of("XA:1@original")),
                    null,
                    Map.of(PATH, List.of("XA:1@original"))))
        .isInstanceOf(ModelOutputInvalidException.class);

    assertThat(calls).hasValue(2);
    assertThat(seen).as("a failed call is still reported for the cost ledger").hasSize(1);
  }

  @Test
  void shouldRequireValuesForEveryDeclaredEnum() throws Exception {
    ModelGateway gateway = gateway(Map.of(), new AtomicInteger(), new ArrayList<>());

    assertThatThrownBy(
            () ->
                gateway.call(
                    prompt(), Map.of("candidates", List.of("XA:1@original")), null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PATH);
  }

  @Test
  void shouldNotRepairACallThatStoppedAtItsOutputCap_andKeepItsUsage() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    ChatPort capped =
        new ChatPort() {
          @Override
          public String provider() {
            return "anthropic";
          }

          @Override
          public ChatResponse call(ChatRequest request) {
            calls.incrementAndGet();
            throw new ModelOutputInvalidException(
                "anthropic-max-tokens", new TokenUsage(500, 150, 0, 0));
          }
        };
    List<LlmCall> seen = new ArrayList<>();
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5", "claude-haiku-5-5", null, "claude-haiku-5-5", null, "q", "b");
    ModelGateway gateway =
        new ModelGateway(
            new PromptRegistry(MAPPER),
            new PromptRenderer(MAPPER),
            List.of(capped),
            models,
            List.of(seen::add));

    assertThatThrownBy(
            () ->
                gateway.call(
                    prompt(),
                    Map.of("candidates", List.of("XA:1@original")),
                    null,
                    Map.of(PATH, List.of("XA:1@original"))))
        .isInstanceOfSatisfying(
            ModelOutputInvalidException.class,
            e -> {
              assertThat(e.stoppedAtCap()).isTrue();
              assertThat(e.usage().outputTokens()).isEqualTo(150);
            });

    assertThat(calls).as("no wasted repair call").hasValue(1);
    assertThat(seen).hasSize(1);
  }
}
