package bd.shromikai.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-63: JSON Schema 2020-12 documents become OpenAPI 3.0 component schemas for the generated
 * frontend types: nullable types, URN references, constants and enums with null.
 */
@Tag("unit")
@Requirement("FR-63")
class ApiSchemaComponentsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode convert(String json) throws Exception {
    return ApiSchemaComponents.toOpenApi30(MAPPER.readTree(json));
  }

  @Test
  void aNullableTypeArrayBecomesTypeWithNullable() throws Exception {
    JsonNode out = convert("{\"type\":[\"string\",\"null\"],\"maxLength\":5}");
    assertThat(out.path("type").asText()).isEqualTo("string");
    assertThat(out.path("nullable").asBoolean()).isTrue();
    assertThat(out.path("maxLength").asInt()).isEqualTo(5);
  }

  @Test
  void anEnumWithNullDropsTheNullAndIsNullableWithAType() throws Exception {
    JsonNode out = convert("{\"enum\":[\"A\",\"B\",null]}");
    assertThat(out.path("enum")).hasSize(2);
    assertThat(out.path("type").asText()).isEqualTo("string");
    assertThat(out.path("nullable").asBoolean()).isTrue();
  }

  @Test
  void aConstBecomesASingleValueEnum() throws Exception {
    JsonNode out = convert("{\"const\":\"SUCCEEDED\"}");
    assertThat(out.path("enum")).hasSize(1);
    assertThat(out.path("enum").get(0).asText()).isEqualTo("SUCCEEDED");
    assertThat(out.path("type").asText()).isEqualTo("string");
    assertThat(out.has("const")).isFalse();
  }

  @Test
  void aUrnReferenceBecomesAComponentReference() throws Exception {
    JsonNode out = convert("{\"$ref\":\"urn:shromikai:schema:clause-type:1\"}");
    assertThat(out.path("$ref").asText()).isEqualTo("#/components/schemas/ClauseType");
  }

  @Test
  void aNullableReferenceIsWrappedBecauseOpenApi30IgnoresSiblingsOfRef() throws Exception {
    JsonNode out =
        convert("{\"oneOf\":[{\"$ref\":\"urn:shromikai:schema:memo:1\"},{\"type\":\"null\"}]}");
    assertThat(out.at("/allOf/0/$ref").asText()).isEqualTo("#/components/schemas/Memo");
    assertThat(out.path("nullable").asBoolean()).isTrue();
    assertThat(out.has("oneOf")).isFalse();
  }

  @Test
  void localDefsAreInlinedAndDefsAndIdsAreDropped() throws Exception {
    JsonNode out =
        convert(
            """
            {"$schema":"x","$id":"urn:shromikai:schema:a:1","type":"object",
             "properties":{"o":{"$ref":"#/$defs/outcome"}},
             "$defs":{"outcome":{"enum":["PASS","FAIL"]}}}
            """);
    assertThat(out.has("$defs")).isFalse();
    assertThat(out.has("$id")).isFalse();
    assertThat(out.has("$schema")).isFalse();
    assertThat(out.at("/properties/o/enum")).hasSize(2);
  }

  @Test
  void conditionalsAreDroppedBecauseOpenApi30HasNoIfThen() throws Exception {
    JsonNode out =
        convert(
            """
            {"type":"object","allOf":[
              {"if":{"properties":{"a":{"const":"x"}}},"then":{"required":["b"]}}]}
            """);
    assertThat(out.has("allOf")).isFalse();
  }

  @Test
  void anAllOfWithARefAndAConstrainingObjectIsKept() throws Exception {
    JsonNode out =
        convert(
            """
            {"allOf":[{"$ref":"urn:shromikai:schema:verification-result:1"},
                      {"properties":{"status":{"const":"VERIFIED"}}}]}
            """);
    assertThat(out.at("/allOf/0/$ref").asText())
        .isEqualTo("#/components/schemas/VerificationResult");
    assertThat(out.at("/allOf/1/properties/status/enum/0").asText()).isEqualTo("VERIFIED");
  }

  @Test
  void componentNamesArePascalCaseOfTheFileName() {
    assertThat(ApiSchemaComponents.componentName("sse-stage-event")).isEqualTo("SseStageEvent");
    assertThat(ApiSchemaComponents.componentName("analysis-result")).isEqualTo("AnalysisResult");
  }

  @Test
  void everyApiSchemaFileBecomesAComponentThatKeepsItsRequiredList() {
    Map<String, JsonNode> all = ApiSchemaComponents.all();
    assertThat(all).containsKeys("AnalysisResult", "CompareResult", "ReviewItem", "UsageReport");
    assertThat(all.get("AnalysisResult").path("required")).isNotEmpty();
    assertThat(all.get("ReviewItemPatch").has("required")).isFalse();
    assertThat(all.toString()).doesNotContain("urn:shromikai").doesNotContain("\"const\"");
  }
}
