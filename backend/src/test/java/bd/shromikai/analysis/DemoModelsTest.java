package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-69, R-04, R-12: the scripted stand-ins of the {@code fake-models} profile answer in the schema
 * of their prompt, name themselves as scripted (never as a real model), and find only the invented
 * names that are in the text they are given.
 */
@Tag("unit")
@Requirement("FR-69")
class DemoModelsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemas SCHEMAS = new JsonSchemas();

  private FakeChatPort fake;

  @BeforeEach
  void install() {
    fake = new FakeChatPort("classpath:fixtures/llm", MAPPER, SCHEMAS, "default");
    new DemoModels(fake, new SampleContract(), MAPPER).install();
  }

  private ChatResponse ask(String promptId, int version, String text) {
    return fake.call(
        new ChatRequest(
            promptId,
            version,
            ModelRole.FAST,
            "claude-haiku-5-5",
            "system",
            SensitiveText.of(text),
            MAPPER.createObjectNode().put("type", "object"),
            100,
            Duration.ofSeconds(5),
            null,
            null));
  }

  @Test
  void everyStandInAnswersInTheSchemaOfItsPrompt() {
    for (String[] prompt :
        new String[][] {
          {"pii-extract", "1"},
          {"obligations", "1"},
          {"memo-bn", "1"},
          {"memo-en", "1"},
          {"baseline", "1"}
        }) {
      ChatResponse response = ask(prompt[0], Integer.parseInt(prompt[1]), "text");
      assertThat(
              SCHEMAS.validate(
                  "schemas/llm/" + prompt[0] + ".v" + prompt[1] + ".schema.json",
                  response.output()))
          .as(prompt[0])
          .isEmpty();
    }
  }

  @Test
  void aStandInNamesItselfAsScriptedAndNeverAsARealModel() {
    for (String prompt : List.of("pii-extract", "obligations", "memo-bn", "memo-en", "baseline")) {
      assertThat(ask(prompt, 1, "text").modelId()).as(prompt).isEqualTo(DemoModels.STAND_IN_MODEL);
    }
  }

  @Test
  void theLocalPrivacyStandInFindsOnlyTheInventedNamesThatAreInTheText() {
    String text = SyntheticContracts.text("syn-en-02");

    JsonNode found = ask("pii-extract", 1, text).output();
    JsonNode none = ask("pii-extract", 1, "An employer and a worker agree on terms.").output();

    assertThat(found.path("persons")).extracting(JsonNode::asText).contains("Karim Demo Hossain");
    assertThat(found.path("organisations"))
        .extracting(JsonNode::asText)
        .contains("Demo Guard Services");
    assertThat(none.path("persons")).isEmpty();
    assertThat(none.path("organisations")).isEmpty();
  }

  @Test
  void theScriptedBaselineCitesOnlyKeysThatTheVerifierMustReject() {
    JsonNode baseline = ask("baseline", 1, "text").output();

    assertThat(baseline.path("findings")).hasSizeGreaterThanOrEqualTo(2);
    baseline
        .path("findings")
        .forEach(
            finding ->
                assertThat(finding.path("citations").get(0).path("quote").asText())
                    .as("a fabricated quote, never statute text")
                    .isNotBlank());
  }
}
