package bd.shromikai.shared.ai.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/**
 * Reduces an output schema to the subset the Claude structured-output API accepts (ADR-0005 rule
 * 1): size and range keywords are dropped from the copy that is sent. The full schema still
 * validates every answer locally (R-04), so a limit such as {@code maxItems} keeps its effect: an
 * answer that breaks it is invalid and gets one repair. The prompt text is what tells the model the
 * limits.
 */
final class AnthropicSchemaSubset {

  /** Keywords the API does not take, to be removed everywhere in the schema. */
  private static final Set<String> UNSUPPORTED =
      Set.of(
          "minimum",
          "maximum",
          "exclusiveMinimum",
          "exclusiveMaximum",
          "multipleOf",
          "minLength",
          "maxLength",
          "pattern",
          "maxItems",
          "uniqueItems",
          "minContains",
          "maxContains",
          "format");

  private AnthropicSchemaSubset() {}

  /** A reduced copy; the argument is not changed. */
  static JsonNode reduce(JsonNode schema) {
    JsonNode copy = schema.deepCopy();
    strip(copy);
    return copy;
  }

  private static void strip(JsonNode node) {
    if (node instanceof ObjectNode object) {
      boolean isSchemaNode = object.has("type") || object.has("properties") || object.has("items");
      if (isSchemaNode) {
        UNSUPPORTED.forEach(object::remove);
        JsonNode minItems = object.get("minItems");
        if (minItems != null && minItems.asInt() > 1) {
          object.remove("minItems");
        }
      }
      for (var entry : object.properties()) {
        // The values of "properties" are schemas keyed by property name; "enum" values are data.
        if (!entry.getKey().equals("enum") && !entry.getKey().equals("const")) {
          strip(entry.getValue());
        }
      }
    } else if (node instanceof ArrayNode array) {
      array.forEach(AnthropicSchemaSubset::strip);
    }
  }
}
