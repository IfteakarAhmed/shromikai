package bd.shromikai.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** C30: the per-request enum restricts citation section keys (architecture §8.3). */
@Tag("unit")
@Requirement("FR-37")
class SchemaEnumsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PATH = "$.findings[*].citations[*].sectionKey";

  private static JsonNode schema() throws Exception {
    return MAPPER.readTree(
        """
        {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
         "additionalProperties":false,"required":["findings"],
         "properties":{"findings":{"type":"array","items":{"type":"object",
          "additionalProperties":false,"required":["citations"],
          "properties":{"citations":{"type":"array","items":{"type":"object",
           "additionalProperties":false,"required":["sectionKey"],
           "properties":{"sectionKey":{"type":"string","pattern":"^x$"}}}}}}}}}
        """);
  }

  private static JsonNode answer(String key) throws Exception {
    return MAPPER.readTree("{\"findings\":[{\"citations\":[{\"sectionKey\":\"" + key + "\"}]}]}");
  }

  @Test
  void shouldAcceptCandidateKeysAndRejectOthers() throws Exception {
    JsonNode restricted =
        SchemaEnums.restrict(schema(), PATH, List.of("XA:1@original", "XA:2@original"));

    assertThat(JsonSchemas.validate(restricted, answer("XA:2@original"))).isEmpty();
    assertThat(JsonSchemas.validate(restricted, answer("XA:999@original"))).isNotEmpty();
  }

  @Test
  void shouldNotChangeTheOriginalSchema() throws Exception {
    JsonNode original = schema();
    String before = original.toString();

    SchemaEnums.restrict(original, PATH, List.of("XA:1@original"));

    assertThat(original.toString()).isEqualTo(before);
  }

  @Test
  void shouldOfferOnlyAnImpossibleKey_whenThereAreNoCandidates() throws Exception {
    JsonNode restricted = SchemaEnums.restrict(schema(), PATH, List.of());

    assertThat(JsonSchemas.validate(restricted, answer("XA:1@original"))).isNotEmpty();
    assertThat(JsonSchemas.validate(restricted, answer(SchemaEnums.NO_CANDIDATE))).isEmpty();
  }

  @Test
  void shouldRejectPathsOutsideTheSchema() throws Exception {
    assertThatThrownBy(() -> SchemaEnums.restrict(schema(), "$.nothing[*].here", List.of("a")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SchemaEnums.restrict(schema(), "findings", List.of("a")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
