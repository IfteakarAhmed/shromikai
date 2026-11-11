package bd.shromikai.shared.ai;

/**
 * Token counts reported by a provider for one call. {@code inputTokens} are the uncached input
 * tokens; cache reads and writes are reported separately because they are priced differently.
 */
public record TokenUsage(
    long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

  public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0);

  public TokenUsage(long inputTokens, long outputTokens, long cacheReadTokens) {
    this(inputTokens, outputTokens, cacheReadTokens, 0);
  }

  /** All tokens the model read: uncached input plus cache reads and writes. */
  public long totalInputTokens() {
    return inputTokens + cacheReadTokens + cacheWriteTokens;
  }

  public TokenUsage plus(TokenUsage other) {
    return new TokenUsage(
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        cacheReadTokens + other.cacheReadTokens,
        cacheWriteTokens + other.cacheWriteTokens);
  }
}
