package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Golden;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FR-37, FR-47, FR-48: three synthetic contracts through the real pipeline (Spring, PostgreSQL,
 * verifier, rule engine, corrective loop, review items, counter-offers) produce the expected
 * findings snapshot. The model answers are <strong>scripted stand-ins</strong> ({@code P6Stubs}),
 * not recorded model output, and the act is fictional; the snapshot proves the pipeline's
 * behaviour, not model quality (that is M-06 to M-10 in the evaluation, P10).
 */
@Requirement("FR-37")
@Requirement("FR-47")
@Requirement("FR-48")
class GoldenAnalysisIT extends DevPipelineIT {

  private static final ObjectMapper PRETTY =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  /** The stable part of a result: no IDs of review items, no times, costs or model strings. */
  static ObjectNode snapshot(JsonNode result) {
    ObjectNode out = PRETTY.createObjectNode();
    ArrayNode findings = out.putArray("findings");
    for (JsonNode f : result.get("findings")) {
      ObjectNode item = findings.addObject();
      item.put("id", f.get("id").asText());
      item.put("kind", f.get("kind").asText());
      item.put("severity", f.get("severity").asText());
      item.put("source", f.get("source").asText());
      item.put("nonObvious", f.get("nonObvious").asBoolean());
      item.put("clauseType", f.get("clauseType").asText());
      item.set("clauseIds", f.get("clauseIds"));
      item.put("verificationStatus", f.get("verificationStatus").asText());
      ArrayNode citations = item.putArray("citations");
      for (JsonNode c : f.get("citations")) {
        ObjectNode citation = citations.addObject();
        citation.put("sectionKey", c.get("sectionKey").asText());
        citation.put("attempt", c.get("verification").get("attempt").asInt());
        citation.put(
            "support", c.get("verification").get("checks").get("support").get("label").asText());
      }
      item.put("redline", f.hasNonNull("redlineId"));
      item.put("reviewItem", f.hasNonNull("reviewItemId"));
    }
    ArrayNode warnings = out.putArray("warnings");
    result.get("warnings").forEach(w -> warnings.add(w.get("code").asText()));
    out.put("reviewItems", result.get("reviewItems").asInt());
    ArrayNode stages = out.putArray("stages");
    result.get("trace").forEach(t -> stages.add(t.get("stage").asText()));
    return out;
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"syn-en-02", "syn-bn-01", "syn-en-01"})
  void shouldProduceTheExpectedFindingsSnapshot(String contract) throws Exception {
    JsonNode result = findings(run(contract, "WORKER"));

    ObjectNode actual = snapshot(result);
    String actualText = PRETTY.writeValueAsString(actual) + "\n";

    assertThat(actualText)
        .isEqualTo(Golden.expect("analysis/" + contract + ".findings.json", actualText));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"syn-en-02", "syn-bn-01", "syn-en-01"})
  void shouldKeepTheInvariantsOfTheVerificationFirstDesign(String contract) {
    String id = run(contract, "WORKER");
    JsonNode result = findings(id);

    int verified = 0;
    int uncertain = 0;
    for (JsonNode f : result.get("findings")) {
      if (f.get("verificationStatus").asText().equals("VERIFIED")) {
        verified++;
        assertThat(f.get("citations")).as(f.get("id").asText()).isNotEmpty();
        for (JsonNode c : f.get("citations")) {
          assertThat(c.get("verification").get("status").asText()).isEqualTo("VERIFIED");
        }
      } else {
        uncertain++;
        assertThat(f.get("citations")).as("UNCERTAIN shows no citation").isEmpty();
        assertThat(f.get("redlineId").isNull()).as("no counter-offer for UNCERTAIN").isTrue();
        assertThat(f.get("reviewItemId").isNull()).as("review item for UNCERTAIN").isFalse();
      }
    }
    assertThat(verified).isPositive();
    assertThat(uncertain).as("the scripted model's invented quote ends UNCERTAIN").isPositive();
    assertThat(
            jdbc.sql("select count(*) from review_item where analysis_id = cast(:id as uuid)")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo((long) uncertain);
    var run =
        jdbc.sql(
                "select status, finding_count, uncertain_finding_count, verified_citation_count,"
                    + " prompt_versions::text from analysis_run where id = cast(:id as uuid)")
            .param("id", id)
            .query()
            .singleRow();
    assertThat(run.get("status")).isEqualTo("SUCCEEDED");
    assertThat(((Number) run.get("finding_count")).intValue()).isEqualTo(verified + uncertain);
    assertThat(run.get("prompt_versions").toString()).contains("risk-analysis");
    List<String> steps =
        jdbc.sql(
                "select step from analysis_trace where analysis_id = cast(:id as uuid) order by id")
            .param("id", id)
            .query(String.class)
            .list();
    assertThat(steps)
        .contains(
            "INTAKE",
            "REDACT",
            "CLASSIFY",
            "RETRIEVE",
            "RULES",
            "RISK",
            "VERIFY",
            "COMPOSE",
            "COMPLETE");
  }
}
