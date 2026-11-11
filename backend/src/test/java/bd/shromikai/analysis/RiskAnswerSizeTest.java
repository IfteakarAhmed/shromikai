package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.Prompt;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-37, ADR-0016: the largest answer the {@code risk-analysis} v2 schema allows fits its output
 * cap with margin. A live run of v1 (cap 1,400, no bounds) was cut off on every call, and a failed
 * call costs money, so the bound is a test and not a hope. The token estimate uses rates measured
 * on recorded real Haiku answers (JSON text about 0.65 tokens per character, Bangla about 0.9;
 * fitted on 15 recorded responses with a residual of at most 22 tokens), rounded up.
 */
@Tag("unit")
@Requirement("FR-37")
class RiskAnswerSizeTest {

  /** Measured rates, rounded up: tokens per character. */
  private static final double ASCII_MEASURED = 0.70;

  private static final double BANGLA_MEASURED = 0.90;

  /** A harsher assumption the answer must still fit under, with no margin. */
  private static final double ASCII_HARSH = 0.80;

  private static final double BANGLA_HARSH = 1.00;

  private final PromptRegistry registry = new PromptRegistry(TestGateways.MAPPER);
  private final Prompt v2 = registry.get("risk-analysis", 2);

  private static String bangla(int chars) {
    return "ক".repeat(chars);
  }

  /** The largest valid answer: 3 findings, every field at its limit, written in Bangla. */
  private ObjectNode maximumAnswer(int findings, int citations, int explanation) {
    ObjectNode out = TestGateways.MAPPER.createObjectNode();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode list = out.putArray("findings");
    for (int i = 0; i < findings; i++) {
      ObjectNode f = list.addObject();
      f.put("title", bangla(50));
      f.put("kind", "INTERACTION");
      f.put("severity", "CRITICAL");
      f.put("non_obvious", false);
      f.put("clause_type", "DISCIPLINE_AND_MISCONDUCT");
      ArrayNode ids = f.putArray("clause_ids");
      for (int c = 1; c <= 5; c++) {
        ids.add("c" + (100 + c));
      }
      f.put("explanation", bangla(explanation));
      ArrayNode cites = f.putArray("citations");
      for (int c = 0; c < citations; c++) {
        cites
            .addObject()
            .put("section_key", "BLA-2006:1234@amend-2026")
            .put("quote", bangla(120))
            .put("claim", bangla(100));
      }
      f.put("confidence", 0.95);
    }
    return out;
  }

  /** Estimated tokens of the compact JSON of an answer at the given rates. */
  private static double tokens(JsonNode answer, double asciiRate, double banglaRate) {
    String json = answer.toString();
    long bangla = json.codePoints().filter(cp -> cp >= 0x0980 && cp <= 0x09FF).count();
    long other = json.length() - bangla;
    return bangla * banglaRate + other * asciiRate;
  }

  @Test
  void shouldAcceptTheMaximumAnswerAndRejectOneStepBeyondIt() {
    ObjectNode max = maximumAnswer(3, 2, 160);

    assertThat(JsonSchemas.validate(v2.outputSchema(), max)).isEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), maximumAnswer(4, 2, 160)))
        .as("a fourth finding")
        .isNotEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), maximumAnswer(3, 3, 160)))
        .as("a third citation")
        .isNotEmpty();
    assertThat(JsonSchemas.validate(v2.outputSchema(), maximumAnswer(3, 2, 161)))
        .as("a 161-character explanation")
        .isNotEmpty();
  }

  @Test
  void shouldFitTheCapWithMarginAtMeasuredRates() {
    double estimate = tokens(maximumAnswer(3, 2, 160), ASCII_MEASURED, BANGLA_MEASURED);

    assertThat(v2.maxOutputTokens()).isEqualTo(3000);
    assertThat(estimate)
        .as("worst-case answer at measured rates, tokens (cap 3000)")
        .isLessThanOrEqualTo(v2.maxOutputTokens() * 0.85);
  }

  @Test
  void shouldStillFitTheCapAtAHarsherAssumption() {
    double estimate = tokens(maximumAnswer(3, 2, 160), ASCII_HARSH, BANGLA_HARSH);

    assertThat(estimate)
        .as("worst-case answer at harsher rates, tokens (cap 3000)")
        .isLessThanOrEqualTo(v2.maxOutputTokens());
  }

  @Test
  void shouldHaveSmallerAnswersFarBelowTheCap() {
    assertThat(tokens(maximumAnswer(1, 1, 160), ASCII_MEASURED, BANGLA_MEASURED))
        .isLessThan(v2.maxOutputTokens() / 3.0);
  }

  @Test
  void shouldHaveStatedTheV1CapThatFailed() {
    Prompt v1 = registry.get("risk-analysis", 1);

    assertThat(v1.maxOutputTokens()).isEqualTo(1400);
    assertThat(JsonSchemas.validate(v1.outputSchema(), maximumAnswer(3, 2, 160)))
        .as("v1 has no why_it_matters-free shape: its answers are a different contract")
        .isNotEmpty();
  }

  @Test
  void shouldKeepTheLimitsOfTheSchemaAndTheTextInStep() {
    JsonNode schema = v2.outputSchema();
    JsonNode finding = schema.at("/properties/findings/items/properties");
    List<String> text = List.of(v2.system());

    assertThat(schema.at("/properties/findings/maxItems").asInt()).isEqualTo(3);
    assertThat(finding.at("/citations/maxItems").asInt()).isEqualTo(2);
    assertThat(finding.at("/title/maxLength").asInt()).isEqualTo(50);
    assertThat(finding.at("/explanation/maxLength").asInt()).isEqualTo(160);
    assertThat(finding.at("/citations/items/properties/quote/maxLength").asInt()).isEqualTo(120);
    assertThat(finding.at("/citations/items/properties/claim/maxLength").asInt()).isEqualTo(100);
    assertThat(text.getFirst())
        .contains("at most 3 findings")
        .contains("at most 2 citations")
        .contains("at most 50 characters")
        .contains("at most 160 characters")
        .contains("20 to 120 characters")
        .contains("at most 100 characters");
  }
}
