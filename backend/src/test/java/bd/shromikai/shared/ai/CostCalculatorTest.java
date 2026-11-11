package bd.shromikai.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.shared.config.PricingProperties.LongContext;
import bd.shromikai.shared.config.PricingProperties.Price;
import bd.shromikai.testsupport.Requirement;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-48, adapter rule 5: prices come from configuration; unknown models count at the top price. */
@Tag("unit")
@Requirement("FR-48")
class CostCalculatorTest {

  private final CostCalculator calculator =
      new CostCalculator(
          new PricingProperties(
              Map.of(
                  "model-small",
                  new Price(1.0, 2.0, 1.25, 2.0, 0.1, new LongContext(1000, 5.0, 10.0, 0, 0, 0)),
                  "model-big",
                  Price.simple(10.0, 20.0),
                  "gemini",
                  Price.simple(0.5, 1.0))));

  @Test
  void shouldPriceInputOutputAndCacheTokensInMicroDollars() {
    long cost = calculator.usdMicros("model-small", new TokenUsage(100, 50, 200, 80));

    // 100 in + 50 out + 200 cache read + 80 cache write, per million tokens
    assertThat(cost).isEqualTo(Math.round((100 * 1.0 + 50 * 2.0 + 200 * 0.1 + 80 * 1.25)));
  }

  @Test
  void shouldUseTheLongContextTier_whenTheInputExceedsTheThreshold() {
    long cost = calculator.usdMicros("model-small", new TokenUsage(2000, 100, 0, 0));

    assertThat(cost).isEqualTo(2000 * 5 + 100 * 10);
  }

  @Test
  void shouldPriceAnyGeminiModelFromTheGeminiEntry() {
    assertThat(calculator.usdMicros("gemini-flash-x", new TokenUsage(1000, 100, 0, 0)))
        .isEqualTo(Math.round(1000 * 0.5 + 100 * 1.0));
  }

  @Test
  void shouldChargeAnUnknownModelAtTheHighestPrice() {
    assertThat(calculator.usdMicros("who-knows", new TokenUsage(10, 10, 0, 0)))
        .isEqualTo(calculator.usdMicros("model-big", new TokenUsage(10, 10, 0, 0)));
  }

  @Test
  void shouldEstimateTheWorstCaseOfACall() {
    assertThat(calculator.worstCaseUsdMicros("model-big", 100, 50))
        .isEqualTo(calculator.usdMicros("model-big", new TokenUsage(100, 50, 0, 0)));
  }
}
