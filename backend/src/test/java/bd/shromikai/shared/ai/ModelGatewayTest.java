package bd.shromikai.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.ai.fake.FailureMode;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** R-04: the gateway returns only schema-valid output; one repair, then failure. */
@Tag("unit")
@Requirement("FR-21")
class ModelGatewayTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private FakeChatPort fake;
  private ModelGateway gateway;

  @BeforeEach
  void setUp() {
    fake = new FakeChatPort("fixtures/llm", mapper, new JsonSchemas(), "default");
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            null,
            "claude-haiku-5-5",
            null,
            "qwen2.5:7b-instruct",
            "bge-m3");
    gateway =
        new ModelGateway(
            new PromptRegistry(mapper), new PromptRenderer(mapper), List.of(fake), models);
  }

  private static final String VALID =
      "{\"status\":\"OK\",\"reason\":\"NONE\",\"queries\":[\"annual leave\"]}";

  @Test
  void shouldReturnValidOutputWithPromptVersionAndModel() throws Exception {
    fake.respondWith("query-rewrite", request -> parse(VALID));

    ModelGateway.Result result = gateway.call("query-rewrite", Map.of("clause", "Leave."));

    assertThat(result.output().path("queries").get(0).asText()).isEqualTo("annual leave");
    assertThat(result.promptVersion()).isEqualTo(1);
    assertThat(result.modelId()).isEqualTo("claude-haiku-5-5");
    ChatRequest sent = fake.calls().get(0).request();
    assertThat(sent.role()).isEqualTo(ModelRole.FAST);
    assertThat(sent.temperature()).isNull();
    assertThat(sent.effort()).isEqualTo(Effort.LOW);
  }

  @Test
  void shouldRepairOnceWithTheValidatorErrors() throws Exception {
    fake.respondWith("query-rewrite", request -> parse(VALID));
    fake.failWith("query-rewrite", FailureMode.INVALID_THEN_VALID);

    ModelGateway.Result result = gateway.call("query-rewrite", Map.of("clause", "Leave."));

    assertThat(result.output().path("status").asText()).isEqualTo("OK");
    assertThat(fake.calls()).hasSize(2);
    assertThat(fake.calls().get(1).request().user().reveal()).contains("<validation_errors>");
  }

  @Test
  void shouldFailAfterOneRepairWhenOutputStaysInvalid() {
    fake.failWith("query-rewrite", FailureMode.SCHEMA_INVALID);

    assertThatThrownBy(() -> gateway.call("query-rewrite", Map.of("clause", "Leave.")))
        .isInstanceOf(ModelOutputInvalidException.class);
    assertThat(fake.calls()).hasSize(2);
  }

  @Test
  void shouldPropagateProviderFailuresWithoutRetrying() {
    fake.failWith("query-rewrite", FailureMode.REFUSAL);

    assertThatThrownBy(() -> gateway.call("query-rewrite", Map.of("clause", "Leave.")))
        .isInstanceOf(ModelRefusedException.class);
    assertThat(fake.calls()).hasSize(1);
  }

  private com.fasterxml.jackson.databind.JsonNode parse(String json) {
    try {
      return mapper.readTree(json);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
