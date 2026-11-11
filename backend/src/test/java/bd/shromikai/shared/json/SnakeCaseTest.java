package bd.shromikai.shared.json;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-64: MCP results use snake_case keys (api.md §1) without touching values or enum keys. */
@Tag("unit")
@Requirement("FR-64")
class SnakeCaseTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void camelCaseKeysBecomeSnakeCaseAtEveryDepth() throws Exception {
    JsonNode in =
        MAPPER.readTree(
            "{\"sectionKey\":\"A:1@v\",\"isCurrent\":true,\"items\":[{\"quoteLanguage\":\"en\"}],"
                + "\"nested\":{\"matchedLanguage\":null}}");

    assertThat(SnakeCase.keys(in).toString())
        .isEqualTo(
            "{\"section_key\":\"A:1@v\",\"is_current\":true,\"items\":[{\"quote_language\":\"en\"}],"
                + "\"nested\":{\"matched_language\":null}}");
  }

  @Test
  void constantStyleKeysSuchAsSeveritiesStayAsTheyAre() throws Exception {
    JsonNode in =
        MAPPER.readTree("{\"findingsBySeverity\":{\"CRITICAL\":0,\"HIGH\":6,\"MEDIUM\":1}}");

    assertThat(SnakeCase.keys(in).toString())
        .isEqualTo("{\"findings_by_severity\":{\"CRITICAL\":0,\"HIGH\":6,\"MEDIUM\":1}}");
  }

  @Test
  void valuesAndAlreadySnakeCaseKeysAreUntouched() throws Exception {
    JsonNode in = MAPPER.readTree("{\"already_snake\":\"someCamelValue\",\"n\":3}");

    assertThat(SnakeCase.keys(in)).isEqualTo(in);
  }

  @Test
  void theInputTreeIsNotModified() throws Exception {
    JsonNode in = MAPPER.readTree("{\"sectionKey\":\"x\"}");

    SnakeCase.keys(in);

    assertThat(in.toString()).isEqualTo("{\"sectionKey\":\"x\"}");
  }
}
