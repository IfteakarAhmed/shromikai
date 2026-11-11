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
 * FR-41, ADR-0017: the largest answer the {@code counter-offer} v2 schema allows fits its output
 * cap with margin. In the first live run three of nine v1 answers stopped at the 500-token cap
 * (Bangla rationales of up to 600 characters) and were dropped, so the bound is a test. Token rates
 * are those measured on recorded real answers (see {@code RiskAnswerSizeTest}).
 */
@Tag("unit")
@Requirement("FR-41")
class CounterOfferAnswerSizeTest {

  private static final double ASCII_MEASURED = 0.70;
  private static final double BANGLA_MEASURED = 0.90;
  private static final double ASCII_HARSH = 0.80;
  private static final double BANGLA_HARSH = 1.00;

  private final PromptRegistry registry = new PromptRegistry(TestGateways.MAPPER);
  private final Prompt v2 = registry.get("counter-offer", 2);

  private static ObjectNode answer(int proposed, int rationale) {
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE").put("action", "REPLACE");
    out.put("proposed_text", "ক".repeat(proposed));
    out.put("rationale", "ক".repeat(rationale));
    return out;
  }

  private static double tokens(JsonNode answer, double asciiRate, double banglaRate) {
    String json = answer.toString();
    long bangla = json.codePoints().filter(cp -> cp >= 0x0980 && cp <= 0x09FF).count();
    return bangla * banglaRate + (json.length() - bangla) * asciiRate;
  }

  @Test
  void shouldAcceptTheMaximumAnswerAndRejectOneStepBeyondIt() {
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(600, 140))).isEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(601, 140)))
        .as("a 601-character clause")
        .isNotEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), answer(600, 141)))
        .as("a 141-character rationale")
        .isNotEmpty();
  }

  @Test
  void shouldFitTheCapWithMarginAtMeasuredRates() {
    double estimate = tokens(answer(600, 140), ASCII_MEASURED, BANGLA_MEASURED);

    assertThat(v2.maxOutputTokens()).isEqualTo(900);
    assertThat(estimate)
        .as("worst-case answer at measured rates, tokens (cap 900)")
        .isLessThanOrEqualTo(v2.maxOutputTokens() * 0.85);
  }

  @Test
  void shouldStillFitTheCapAtAHarsherAssumption() {
    assertThat(tokens(answer(600, 140), ASCII_HARSH, BANGLA_HARSH))
        .as("worst-case answer at harsher rates, tokens (cap 900)")
        .isLessThanOrEqualTo(v2.maxOutputTokens());
  }

  @Test
  void shouldHaveTheV1CapThatFailedAndLeaveV1AsItWas() {
    Prompt v1 = registry.get("counter-offer", 1);

    assertThat(v1.maxOutputTokens()).isEqualTo(500);
    assertThat(registry.latest("counter-offer").version()).isEqualTo(2);
    assertThat(JsonSchemas.validate(v1.outputSchema(), answer(600, 600)))
        .as("v1 allowed a 600-character rationale and had no bound on the clause")
        .isEmpty();
  }

  @Test
  void shouldKeepTheLimitsOfTheSchemaAndTheTextInStep() {
    JsonNode props = v2.outputSchema().path("properties");

    assertThat(props.at("/proposed_text/maxLength").asInt()).isEqualTo(600);
    assertThat(props.at("/rationale/maxLength").asInt()).isEqualTo(140);
    assertThat(v2.system())
        .contains("at most 600 characters")
        .contains("at most 140 characters")
        .contains("one plain sentence");
  }
}
