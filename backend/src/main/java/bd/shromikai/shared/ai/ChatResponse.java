package bd.shromikai.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The structured output of one call. {@code output} is parsed JSON that has <em>not</em> yet been
 * validated against the prompt's schema; validation and repair belong to the model gateway (R-04).
 *
 * @param modelId the model that actually answered (may differ after a server-side fallback)
 * @param recorded {@code true} when replayed from a fixture (provenance shows "recorded")
 */
public record ChatResponse(JsonNode output, String modelId, TokenUsage usage, boolean recorded) {}
