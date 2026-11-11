package bd.shromikai.shared.ai.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** ADR-0005 rule 1, ADR-0016: size keywords are not sent to the API but still validate locally. */
@Tag("unit")
@Requirement("FR-37")
class AnthropicSchemaSubsetTest {

  private static final JsonNode SCHEMA =
      TestGateways.json(
          """
          {"type":"object","additionalProperties":false,"required":["items"],
           "properties":{"items":{"type":"array","maxItems":2,"minItems":1,
             "items":{"type":"object","additionalProperties":false,"required":["name","kind"],
               "properties":{"name":{"type":"string","maxLength":5,"minLength":1,"pattern":"^a"},
                             "kind":{"enum":["maxLength","x"],"type":"string"},
                             "score":{"type":"number","minimum":0,"maximum":1}}}}}}
          """);

  @Test
  void shouldDropSizeKeywordsEverywhereButKeepTheStructure() {
    JsonNode reduced = AnthropicSchemaSubset.reduce(SCHEMA);
    String text = reduced.toString();

    assertThat(text)
        .doesNotContain("maxItems")
        .doesNotContain("minLength")
        .doesNotContain("pattern")
        .doesNotContain("minimum")
        .doesNotContain("maximum");
    assertThat(text)
        .contains("\"additionalProperties\":false")
        .contains("\"required\":[\"name\",\"kind\"]");
    assertThat(reduced.at("/properties/items/minItems").asInt())
        .as("minItems 1 is accepted")
        .isEqualTo(1);
    assertThat(reduced.at("/properties/items/items/properties/kind/enum/0").asText())
        .as("enum values are data, not keywords")
        .isEqualTo("maxLength");
  }

  @Test
  void shouldNotChangeTheOriginalSchemaSoLocalValidationKeepsTheLimits() {
    String before = SCHEMA.toString();
    AnthropicSchemaSubset.reduce(SCHEMA);

    assertThat(SCHEMA.toString()).isEqualTo(before);
    JsonNode tooMany =
        TestGateways.json(
            "{\"items\":[{\"name\":\"a\",\"kind\":\"x\"},{\"name\":\"a\",\"kind\":\"x\"},{\"name\":\"a\",\"kind\":\"x\"}]}");
    JsonNode tooLong = TestGateways.json("{\"items\":[{\"name\":\"aaaaaaaa\",\"kind\":\"x\"}]}");
    assertThat(JsonSchemas.validate(SCHEMA, tooMany)).isNotEmpty();
    assertThat(JsonSchemas.validate(SCHEMA, tooLong)).isNotEmpty();
    assertThat(JsonSchemas.validate(AnthropicSchemaSubset.reduce(SCHEMA), tooMany)).isEmpty();
  }
}
