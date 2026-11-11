package bd.shromikai.shared.ai;

import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.shared.config.PricingProperties.Price;
import org.springframework.stereotype.Component;

/**
 * Prices a call from its token usage with the configured prices (architecture §6 adapter rule 5);
 * prices live in {@code shromikai.pricing.*}, never in code. The result is in micro-US-dollars so
 * sums stay exact. A model without a configured price is charged at the highest configured price,
 * so a missing entry can only make the budget stricter.
 */
@Component
public final class CostCalculator {

  private final PricingProperties pricing;

  public CostCalculator(PricingProperties pricing) {
    this.pricing = pricing;
  }

  public long usdMicros(String modelId, TokenUsage usage) {
    Price price = pricing.priceFor(modelId);
    if (price.longContext() != null
        && usage.totalInputTokens() > price.longContext().aboveInputTokens()) {
      price = price.longContext().asPrice();
    }
    double usd =
        (usage.inputTokens() * price.input()
                + usage.outputTokens() * price.output()
                + usage.cacheReadTokens() * price.cacheRead()
                + usage.cacheWriteTokens() * price.cacheWrite5m())
            / 1_000_000.0;
    return Math.round(usd * 1_000_000.0);
  }

  /** Worst-case cost of one call before it is made: {@code inputTokens} in, the cap out. */
  public long worstCaseUsdMicros(String modelId, long inputTokens, long maxOutputTokens) {
    return usdMicros(modelId, new TokenUsage(inputTokens, maxOutputTokens, 0, 0));
  }
}
