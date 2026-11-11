package bd.shromikai.shared.config;

import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * US$ per million tokens by model ID ({@code shromikai.pricing.*}, architecture §6). Prices come
 * from configuration only. The {@code gemini} entry prices whichever Gemini model is configured.
 */
@ConfigurationProperties("shromikai")
public record PricingProperties(@Nullable Map<String, Price> pricing) {

  /** A price list; cache prices default to zero for providers without caching. */
  public record Price(
      double input,
      double output,
      double cacheWrite5m,
      double cacheWrite1h,
      double cacheRead,
      @Nullable LongContext longContext) {

    /** A price without cache tiers (a record with two constructors would not bind). */
    public static Price simple(double input, double output) {
      return new Price(input, output, 0, 0, 0, null);
    }
  }

  /** The higher tier for prompts above {@code aboveInputTokens} input tokens. */
  public record LongContext(
      long aboveInputTokens,
      double input,
      double output,
      double cacheWrite5m,
      double cacheWrite1h,
      double cacheRead) {

    public Price asPrice() {
      return new Price(input, output, cacheWrite5m, cacheWrite1h, cacheRead, null);
    }
  }

  public Map<String, Price> prices() {
    return pricing == null ? Map.of() : pricing;
  }

  /** The price of a model; the highest configured price when the model has none. */
  public Price priceFor(String modelId) {
    Map<String, Price> all = prices();
    Price exact = all.get(modelId);
    if (exact != null) {
      return exact;
    }
    if (modelId.startsWith("gemini") && all.containsKey("gemini")) {
      return all.get("gemini");
    }
    return all.values().stream()
        .max(Comparator.comparingDouble(p -> p.input() + p.output()))
        .orElse(Price.simple(0, 0));
  }
}
