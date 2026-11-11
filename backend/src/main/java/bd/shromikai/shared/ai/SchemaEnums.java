package bd.shromikai.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restricts a JSON Schema node to a per-request set of allowed values (architecture §8.3): the node
 * addressed by a {@code $.a[*].b} path gets an {@code enum}. Works on a copy; the prompt's own
 * schema is never changed.
 */
public final class SchemaEnums {

  /**
   * Stand-in when a request has no allowed value at all: it matches the section-key pattern but
   * names no section, so an answer that cites it is rejected by the verifier (NOT_IN_CANDIDATES).
   */
  public static final String NO_CANDIDATE = "none:0@none";

  private static final Pattern SEGMENT = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(\\[\\*])?");

  private SchemaEnums() {}

  /**
   * @return a copy of {@code schema} in which the node at {@code path} allows only {@code values}
   * @throws IllegalArgumentException if the path does not address a schema node
   */
  public static JsonNode restrict(JsonNode schema, String path, Collection<String> values) {
    if (!path.startsWith("$.")) {
      throw new IllegalArgumentException("Dynamic enum path must start with $.: " + path);
    }
    ObjectNode copy = schema.deepCopy();
    JsonNode node = copy;
    for (String segment : path.substring(2).split("\\.")) {
      Matcher m = SEGMENT.matcher(segment);
      if (!m.matches()) {
        throw new IllegalArgumentException("Unsupported dynamic enum path: " + path);
      }
      node = node.path("properties").path(m.group(1));
      if (m.group(2) != null) {
        node = node.path("items");
      }
      if (node.isMissingNode()) {
        throw new IllegalArgumentException("Dynamic enum path not in schema: " + path);
      }
    }
    ObjectNode target = (ObjectNode) node;
    ArrayNode allowed = target.putArray("enum");
    if (values.isEmpty()) {
      allowed.add(NO_CANDIDATE);
    } else {
      values.stream().distinct().sorted().forEach(allowed::add);
    }
    target.remove("pattern");
    return copy;
  }
}
