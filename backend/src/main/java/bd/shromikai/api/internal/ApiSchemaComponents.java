package bd.shromikai.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Turns the JSON Schema 2020-12 files under {@code schemas/api} into OpenAPI 3.0 component schemas
 * (FR-63). The files are the one source of truth: the contract tests validate real responses
 * against them, and the generated frontend types come from their OpenAPI form. OpenAPI 3.0 has no
 * nullable type arrays, {@code const}, {@code if/then} or URN references, so those are rewritten;
 * everything else is copied unchanged.
 */
final class ApiSchemaComponents {

  private static final Pattern URN = Pattern.compile("^urn:shromikai:schema:([a-z0-9-]+):\\d+$");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  /** The Problem component is built by hand in {@link OpenApiConfig}; the summary maps to it. */
  private static final String PROBLEM_SUMMARY = "problem-summary";

  private ApiSchemaComponents() {}

  /** {@code analysis-result} becomes {@code AnalysisResult}. */
  static String componentName(String kebab) {
    StringBuilder name = new StringBuilder();
    for (String part : kebab.split("-")) {
      name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    return name.toString();
  }

  /** Every API schema file as an OpenAPI 3.0 component, by component name. */
  static Map<String, JsonNode> all() {
    Map<String, JsonNode> out = new TreeMap<>();
    try {
      Resource[] files =
          new PathMatchingResourcePatternResolver()
              .getResources("classpath*:schemas/api/*.v*.schema.json");
      for (Resource file : files) {
        JsonNode schema;
        try (var in = file.getInputStream()) {
          schema = MAPPER.readTree(in);
        }
        Matcher id = URN.matcher(schema.path("$id").asText());
        if (!id.matches() || id.group(1).equals(PROBLEM_SUMMARY)) {
          continue;
        }
        out.put(componentName(id.group(1)), toOpenApi30(schema));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  /** The OpenAPI 3.0 form of a JSON Schema 2020-12 document. */
  static JsonNode toOpenApi30(JsonNode schema) {
    return convert(schema, schema.path("$defs"));
  }

  private static JsonNode convert(JsonNode node, JsonNode defs) {
    if (!node.isObject()) {
      return node.deepCopy();
    }
    if (node.has("$ref")) {
      return reference(node.get("$ref").asText(), defs);
    }
    ObjectNode out = NODES.objectNode();
    boolean nullable = false;

    if (node.has("oneOf")) {
      List<JsonNode> kept = new ArrayList<>();
      for (JsonNode option : node.get("oneOf")) {
        if ("null".equals(option.path("type").asText()) && option.size() == 1) {
          nullable = true;
        } else {
          kept.add(option);
        }
      }
      if (nullable && kept.size() == 1) {
        JsonNode only = convert(kept.get(0), defs);
        if (only.has("$ref")) {
          out.putArray("allOf").add(only);
        } else {
          out = ((ObjectNode) only).deepCopy();
        }
        copyDescription(node, out);
        out.put("nullable", true);
        return out;
      }
    }

    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      String key = field.getKey();
      JsonNode value = field.getValue();
      switch (key) {
        case "$schema", "$id", "$defs", "if", "then", "else" -> {}
        case "type" -> {
          if (value.isArray()) {
            String type = null;
            for (JsonNode t : value) {
              if ("null".equals(t.asText())) {
                nullable = true;
              } else {
                type = t.asText();
              }
            }
            if (type != null) {
              out.put("type", type);
            }
          } else {
            out.set("type", value);
          }
        }
        case "enum" -> {
          ArrayNode values = out.putArray("enum");
          for (JsonNode v : value) {
            if (v.isNull()) {
              nullable = true;
            } else {
              values.add(v);
            }
          }
        }
        case "const" -> out.putArray("enum").add(value);
        case "properties" -> {
          ObjectNode properties = out.putObject("properties");
          value
              .fields()
              .forEachRemaining(p -> properties.set(p.getKey(), convert(p.getValue(), defs)));
        }
        case "items" -> out.set("items", convert(value, defs));
        case "additionalProperties" ->
            out.set("additionalProperties", value.isObject() ? convert(value, defs) : value);
        case "oneOf", "anyOf", "allOf" -> {
          ArrayNode converted = NODES.arrayNode();
          for (JsonNode option : value) {
            if (!option.has("if")) {
              converted.add(convert(option, defs));
            }
          }
          if (!converted.isEmpty()) {
            out.set(key, converted);
          }
        }
        default -> out.set(key, value.deepCopy());
      }
    }
    if (out.has("enum") && !out.has("type") && !out.path("enum").isEmpty()) {
      out.put("type", typeOf(out.path("enum").get(0)));
    }
    if (nullable) {
      out.put("nullable", true);
    }
    return out;
  }

  private static JsonNode reference(String ref, JsonNode defs) {
    if (ref.startsWith("#/$defs/")) {
      JsonNode target = defs.get(ref.substring("#/$defs/".length()));
      if (target == null) {
        throw new IllegalStateException("Unknown $defs reference " + ref);
      }
      return convert(target, defs);
    }
    Matcher urn = URN.matcher(ref);
    if (!urn.matches()) {
      throw new IllegalStateException("Unsupported $ref " + ref);
    }
    String name = urn.group(1).equals(PROBLEM_SUMMARY) ? "Problem" : componentName(urn.group(1));
    return NODES.objectNode().put("$ref", "#/components/schemas/" + name);
  }

  private static String typeOf(JsonNode value) {
    if (value.isIntegralNumber()) {
      return "integer";
    }
    if (value.isNumber()) {
      return "number";
    }
    return value.isBoolean() ? "boolean" : "string";
  }

  private static void copyDescription(JsonNode from, ObjectNode to) {
    if (from.has("description") && !to.has("description")) {
      to.set("description", from.get("description"));
    }
  }
}
