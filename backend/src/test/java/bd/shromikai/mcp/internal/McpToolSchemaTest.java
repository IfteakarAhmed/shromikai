package bd.shromikai.mcp.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-64, api.md §9: every tool has a valid input schema, a precise description and an example. */
@Tag("unit")
@Requirement("FR-64")
class McpToolSchemaTest {

  private static final List<String> TOOLS =
      List.of(
          "search_bd_law",
          "get_law_section",
          "verify_legal_citation",
          "analyze_employment_contract",
          "list_jurisdiction_packs");

  private final McpToolCatalog catalog = new McpToolCatalog(new ObjectMapper());

  @Test
  void exactlyTheFiveToolsOfTheSpecificationAreDefined() {
    assertThat(catalog.definitions()).extracting(McpToolCatalog.Definition::name).isEqualTo(TOOLS);
  }

  @Test
  void everyInputAndOutputSchemaIsValidJsonSchema202012() {
    for (McpToolCatalog.Definition tool : catalog.definitions()) {
      assertThat(JsonSchemas.metaSchemaErrors(tool.input())).as(tool.name() + " input").isEmpty();
      assertThat(JsonSchemas.metaSchemaErrors(tool.output())).as(tool.name() + " output").isEmpty();
      assertThat(tool.input().path("type").asText()).isEqualTo("object");
      assertThat(tool.input().path("additionalProperties").asBoolean(true)).isFalse();
      assertThat(tool.output().path("required").toString())
          .as(tool.name() + " output carries disclaimer and corpus release")
          .contains("disclaimer", "corpus_release");
    }
  }

  @Test
  void everyToolHasAPreciseDescriptionAndAnExampleThatItsOwnSchemaAccepts() {
    for (McpToolCatalog.Definition tool : catalog.definitions()) {
      assertThat(tool.description()).as(tool.name()).hasSizeGreaterThan(80);
      assertThat(tool.example()).as(tool.name() + " example").isNotNull();
      assertThat(JsonSchemas.validate(tool.input(), tool.example()))
          .as(tool.name() + " example against input schema")
          .isEmpty();
    }
  }

  @Test
  void inputsMatchTheLimitsOfApiMd9() {
    JsonNode search = catalog.definition("search_bd_law").input().path("properties");
    assertThat(search.path("query").path("minLength").asInt()).isEqualTo(1);
    assertThat(search.path("query").path("maxLength").asInt()).isEqualTo(500);
    assertThat(search.path("top_k").path("minimum").asInt()).isEqualTo(1);
    assertThat(search.path("top_k").path("maximum").asInt()).isEqualTo(20);
    assertThat(search.path("top_k").path("default").asInt()).isEqualTo(8);
    assertThat(search.path("language").path("enum").toString()).contains("bn", "en", "any");

    JsonNode verify = catalog.definition("verify_legal_citation").input();
    assertThat(verify.path("properties").path("quoted_text").path("minLength").asInt())
        .isEqualTo(20);
    assertThat(verify.path("properties").path("quoted_text").path("maxLength").asInt())
        .isEqualTo(300);
    assertThat(verify.path("properties").path("claim").path("minLength").asInt()).isEqualTo(20);
    assertThat(verify.path("properties").path("claim").path("maxLength").asInt()).isEqualTo(600);
    assertThat(verify.path("required").toString())
        .contains("act_code", "section_no", "quoted_text", "claim");

    JsonNode analyze = catalog.definition("analyze_employment_contract").input();
    assertThat(analyze.path("properties").path("text").path("minLength").asInt()).isEqualTo(200);
    assertThat(analyze.path("properties").path("text").path("maxLength").asInt()).isEqualTo(60_000);
    assertThat(analyze.path("required").toString()).contains("text", "side");
    assertThat(analyze.path("properties").path("side").path("enum").toString())
        .contains("WORKER", "EMPLOYER");
    assertThat(analyze.path("properties").path("output_language").path("default").asText())
        .isEqualTo("bn");

    assertThat(catalog.definition("list_jurisdiction_packs").input().path("properties")).isEmpty();
  }

  @Test
  void verifyAndAnalyseOutputsNameTheFieldsOfTheSpecification() {
    assertThat(catalog.definition("verify_legal_citation").output().path("required").toString())
        .contains("status", "checks", "reason_codes", "verified_citation");
    assertThat(
            catalog.definition("analyze_employment_contract").output().path("required").toString())
        .contains("analysis_id", "findings", "summary", "provenance");
  }
}
