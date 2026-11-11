package bd.shromikai.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/**
 * The structured output of one call. {@code output} is parsed JSON that has <em>not</em> yet been
 * validated against the prompt's schema; validation and repair belong to the model gateway (R-04).
 *
 * @param modelId the model that actually answered (may differ after a server-side fallback)
 * @param recorded {@code true} when replayed from a fixture (provenance shows "recorded")
 * @param stopReason why the model stopped ({@code end_turn} for a complete answer), or {@code null}
 *     when the provider or a fixture does not say
 */
public record ChatResponse(
    JsonNode output,
    String modelId,
    TokenUsage usage,
    boolean recorded,
    @Nullable String stopReason) {

  public ChatResponse(JsonNode output, String modelId, TokenUsage usage, boolean recorded) {
    this(output, modelId, usage, recorded, null);
  }
}
