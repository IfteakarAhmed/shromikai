package bd.shromikai.shared.ai;

/**
 * A chat model provider (ADR-0005). Implementations: Claude through the Anthropic SDK, Gemini
 * through {@code RestClient}, Ollama through Spring AI, and {@code FakeChatPort} in tests.
 */
public interface ChatPort {

  /** Provider name: {@code anthropic}, {@code gemini}, {@code ollama} or {@code fake}. */
  String provider();

  /**
   * Sends one request and returns the parsed JSON output.
   *
   * @throws ModelException on refusal, timeout, provider failure or unparseable output
   * @throws EgressBlockedException when the egress guard stops a cloud call
   */
  ChatResponse call(ChatRequest request);
}
