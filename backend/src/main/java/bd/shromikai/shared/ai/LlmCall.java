package bd.shromikai.shared.ai;

/**
 * One finished model call as the gateway reports it: content-free (no prompt, no output).
 *
 * @param usage tokens of all provider calls of this gateway call (a repair counts)
 * @param latencyMs wall time of the whole gateway call
 * @param recorded true when replayed from a fixture
 */
public record LlmCall(
    String promptId,
    int promptVersion,
    ModelRole role,
    String modelId,
    TokenUsage usage,
    long latencyMs,
    boolean recorded) {}
