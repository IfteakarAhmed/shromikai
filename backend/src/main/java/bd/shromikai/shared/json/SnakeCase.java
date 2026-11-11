package bd.shromikai.shared.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

/**
 * Renames the object keys of a JSON tree from {@code camelCase} to {@code snake_case}. MCP
 * arguments and results are snake_case while the REST DTOs are camelCase (api.md §1); values are
 * never touched.
 */
public final class SnakeCase {

  private SnakeCase() {}

  /** A deep copy of {@code node} with every object key in snake_case. */
  public static JsonNode keys(JsonNode node) {
    if (node.isObject()) {
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        out.set(of(field.getKey()), keys(field.getValue()));
      }
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = JsonNodeFactory.instance.arrayNode();
      node.forEach(item -> out.add(keys(item)));
      return out;
    }
    return node;
  }

  /**
   * {@code sectionKey} to {@code section_key}. Text already in snake_case and constant-style names
   * such as the severity {@code CRITICAL} (no lower-case letter) are unchanged.
   */
  public static String of(String name) {
    if (name.chars().noneMatch(Character::isLowerCase)) {
      return name;
    }
    StringBuilder out = new StringBuilder(name.length() + 4);
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0 && name.charAt(i - 1) != '_') {
          out.append('_');
        }
        out.append(Character.toLowerCase(c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
