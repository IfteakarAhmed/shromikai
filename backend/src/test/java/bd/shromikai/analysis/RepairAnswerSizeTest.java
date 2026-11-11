package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.Prompt;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-46, ADR-0019: the largest answer the {@code risk-citation-repair} v2 schema allows fits its
 * output cap with margin. In the second live run one of two v1 answers stopped at the 300-token cap
 * (a quote of up to 300 characters and a claim of up to 600 were allowed), which ended the run.
 * Token rates are those measured on recorded real answers (see {@code RiskAnswerSizeTest}).
 */
@Tag("unit")
@Requirement("FR-46")
class RepairAnswerSizeTest {

  private static final double ASCII_MEASURED = 0.70;
  private static final double BANGLA_MEASURED = 0.90;
  private static final double ASCII_HARSH = 0.80;
  private static final double BANGLA_HARSH = 1.00;

  private final PromptRegistry registry = new PromptRegistry(TestGateways.MAPPER);
  private final Prompt v2 = registry.get("risk-citation-repair", 2);

  private static ObjectNode answer(int citations, int quote, int claim) {
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    var list = out.putArray("citations");
    for (int i = 0; i < citations; i++) {
      list.addObject()
          .put("section_key", "BLA-2006:1234@amend-2026")
          .put("quote", "ক".repeat(quote))
          .put("claim", "ক".repeat(claim));
    }
    return out;
  }

  private static double tokens(JsonNode answer, double asciiRate, double banglaRate) {
    String json = answer.toString();
    long bangla = json.codePoints().filter(cp -> cp >= 0x0980 && cp <= 0x09FF).count();
    return bangla * banglaRate + (json.length() - bangla) * asciiRate;
  }

  @Test
  void shouldAcceptTheMaximumAnswerAndRejectOneStepBeyondIt() {
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(1, 120, 100))).isEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(2, 120, 100)))
        .as("a second citation")
        .isNotEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(1, 121, 100)))
        .as("a 121-character quote")
        .isNotEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(1, 120, 101)))
        .as("a 101-character claim")
        .isNotEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(0, 0, 0)))
        .as("an abstention has no citation")
        .isEmpty();
  }

  @Test
  void shouldFitTheCapWithMarginAtMeasuredRates() {
    assertThat(v2.maxOutputTokens()).isEqualTo(600);
    assertThat(tokens(answer(1, 120, 100), ASCII_MEASURED, BANGLA_MEASURED))
        .as("worst-case answer at measured rates, tokens (cap 600)")
        .isLessThanOrEqualTo(v2.maxOutputTokens() * 0.85);
  }

  @Test
  void shouldStillFitTheCapAtAHarsherAssumption() {
    assertThat(tokens(answer(1, 120, 100), ASCII_HARSH, BANGLA_HARSH))
        .as("worst-case answer at harsher rates, tokens (cap 600)")
        .isLessThanOrEqualTo(v2.maxOutputTokens());
  }

  @Test
  void shouldAllowOneCitationOnlyBecauseTwoWouldLeaveNoMarginAtMeasuredRates() {
    assertThat(tokens(answer(2, 120, 100), ASCII_MEASURED, BANGLA_MEASURED))
        .as("two maximum Bangla citations pass the 15% margin of the 600-token cap")
        .isGreaterThan(v2.maxOutputTokens() * 0.85);
  }

  @Test
  void shouldHaveTheV1CapThatFailedAndLeaveV1AsItWas() {
    Prompt v1 = registry.get("risk-citation-repair", 1);

    assertThat(v1.maxOutputTokens()).isEqualTo(300);
    assertThat(registry.latest("risk-citation-repair").version()).isEqualTo(2);
    assertThat(JsonSchemas.validate(v1.outputSchema(), answer(1, 300, 600)))
        .as("v1 allowed a 300-character quote and a 600-character claim")
        .isEmpty();
    assertThat(tokens(answer(1, 300, 600), ASCII_MEASURED, BANGLA_MEASURED))
        .isGreaterThan(v1.maxOutputTokens());
  }

  @Test
  void shouldKeepTheLimitsOfTheSchemaAndTheTextInStep() {
    JsonNode citations = v2.outputSchema().at("/properties/citations");

    assertThat(citations.path("maxItems").asInt()).isEqualTo(1);
    assertThat(citations.at("/items/properties/quote/maxLength").asInt()).isEqualTo(120);
    assertThat(citations.at("/items/properties/claim/maxLength").asInt()).isEqualTo(100);
    assertThat(v2.system())
        .contains("never more than one")
        .contains("20 to 120")
        .contains("20 to 100");
  }
}
