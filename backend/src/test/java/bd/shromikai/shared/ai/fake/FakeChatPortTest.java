package bd.shromikai.shared.ai.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("NFR-19")
class FakeChatPortTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final JsonSchemas schemas = new JsonSchemas();
  private final FakeChatPort fake =
      new FakeChatPort("classpath:fixtures/llm", mapper, schemas, "default");

  static ChatRequest echo(String user) {
    return new ChatRequest(
        "xx-echo",
        1,
        ModelRole.FAST,
        "claude-haiku-5-5",
        "You echo the input as JSON.",
        SensitiveText.of(user),
        new ObjectMapper().createObjectNode().put("type", "object"),
        100,
        Duration.ofSeconds(5),
        null,
        null);
  }

  @Test
  void shouldReplayFixture_whenPromptIdHasOne() {
    ChatResponse response = fake.call(echo("hello"));

    assertThat(response.output().get("echo").asText()).isEqualTo("hello");
    assertThat(response.modelId()).isEqualTo("claude-haiku-5-5");
    assertThat(response.usage().inputTokens()).isEqualTo(12);
    assertThat(response.recorded()).isTrue();
    assertThat(fake.calls()).singleElement().extracting(RecordedCall::caseId).isEqualTo("default");
  }

  @Test
  void shouldFailLoudly_whenFixtureIsMissing() {
    fake.useCase("no-such-case");

    assertThatThrownBy(() -> fake.call(echo("hello")))
        .isInstanceOf(MissingFixtureException.class)
        .hasMessage(
            "No fixture for xx-echo v1 case no-such-case: run ./gradlew recordFixtures"
                + " -Pcase=no-such-case");
  }

  @Test
  void shouldFailLoudly_whenRenderedRequestChanged() {
    assertThatThrownBy(() -> fake.call(echo("a different input")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("prompt or input changed, re-record");
  }

  @Test
  void shouldRejectFixture_whenResponseViolatesSchema() {
    FakeChatPort samples =
        new FakeChatPort("fakechatport-samples/llm", mapper, schemas, "schema-invalid");

    assertThatThrownBy(() -> samples.call(echo("hello")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid fixture")
        .hasMessageContaining("response:");
  }

  @Test
  void shouldRejectFixture_whenShaIsNotFromThisRequest() {
    FakeChatPort samples = new FakeChatPort("fakechatport-samples/llm", mapper, schemas, "default");

    assertThatThrownBy(() -> samples.call(echo("hello")))
        .hasMessageContaining("recorded from a different request");
  }

  @Test
  void shouldUseInlineResponse_whenRegistered() {
    fake.respondWith(
        "xx-echo", request -> mapper.createObjectNode().put("echo", request.promptId()));

    assertThat(fake.call(echo("anything")).output().get("echo").asText()).isEqualTo("xx-echo");
  }

  @Test
  void shouldSimulateEachFailureMode() {
    fake.failWith("xx-echo", FailureMode.TIMEOUT);
    assertThatThrownBy(() -> fake.call(echo("hello"))).isInstanceOf(ModelTimeoutException.class);

    fake.failWith("xx-echo", FailureMode.RATE_LIMITED);
    assertThatThrownBy(() -> fake.call(echo("hello")))
        .isInstanceOfSatisfying(
            UpstreamUnavailableException.class, e -> assertThat(e.httpStatus()).isEqualTo(429));

    fake.failWith("xx-echo", FailureMode.SERVER_ERROR);
    assertThatThrownBy(() -> fake.call(echo("hello")))
        .isInstanceOfSatisfying(
            UpstreamUnavailableException.class, e -> assertThat(e.httpStatus()).isEqualTo(500));

    fake.failWith("xx-echo", FailureMode.REFUSAL);
    assertThatThrownBy(() -> fake.call(echo("hello"))).isInstanceOf(ModelRefusedException.class);

    fake.failWith("xx-echo", FailureMode.SCHEMA_INVALID);
    JsonNode invalid = fake.call(echo("hello")).output();
    assertThat(schemas.validate(FakeChatPort.schemaLocation("xx-echo", 1), invalid)).isNotEmpty();
  }

  @Test
  void shouldReturnInvalidOnceThenValid_whenRepairPathIsSimulated() {
    fake.failWith("xx-echo", FailureMode.INVALID_THEN_VALID);

    JsonNode first = fake.call(echo("hello")).output();
    JsonNode second = fake.call(echo("hello")).output();

    assertThat(first.has("__schemaInvalid")).isTrue();
    assertThat(second.get("echo").asText()).isEqualTo("hello");
    assertThat(fake.calls()).hasSize(2);
  }

  @Test
  void shouldClearState_whenReset() {
    fake.useCase("other");
    fake.failWith("xx-echo", FailureMode.REFUSAL);
    assertThatThrownBy(() -> fake.call(echo("hello"))).isInstanceOf(ModelRefusedException.class);

    fake.reset();

    assertThat(fake.calls()).isEmpty();
    assertThat(fake.call(echo("hello")).output().get("echo").asText()).isEqualTo("hello");
  }

  @Test
  void shouldNameProviderFake() {
    assertThat(fake.provider()).isEqualTo("fake");
  }
}
