package bd.shromikai.shared.ai;

/** Token counts reported by a provider for one call. */
public record TokenUsage(long inputTokens, long outputTokens, long cacheReadTokens) {

  public static final TokenUsage NONE = new TokenUsage(0, 0, 0);
}
