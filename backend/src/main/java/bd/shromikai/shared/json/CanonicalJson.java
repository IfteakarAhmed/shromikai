package bd.shromikai.shared.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The canonical form of a JSON tree after RFC 8785: object keys sorted by their UTF-16 code units,
 * no white space, one form per number, strings escaped as {@code JSON.stringify} does. Used to sign
 * the result envelope, so a client may re-serialise a payload in any key order (FR-55). Numbers are
 * written as the shortest plain decimal; the memo payload holds only integers and values like 0.8,
 * for which this equals the RFC's ECMAScript form.
 */
public final class CanonicalJson {

  private CanonicalJson() {}

  public static String of(JsonNode node) {
    StringBuilder out = new StringBuilder();
    write(node, out);
    return out.toString();
  }

  private static void write(JsonNode node, StringBuilder out) {
    if (node.isObject()) {
      List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
      Iterator<Map.Entry<String, JsonNode>> it = node.fields();
      it.forEachRemaining(fields::add);
      fields.sort((a, b) -> a.getKey().compareTo(b.getKey())); // String order is by UTF-16 unit
      out.append('{');
      for (int i = 0; i < fields.size(); i++) {
        if (i > 0) {
          out.append(',');
        }
        string(fields.get(i).getKey(), out);
        out.append(':');
        write(fields.get(i).getValue(), out);
      }
      out.append('}');
    } else if (node.isArray()) {
      out.append('[');
      for (int i = 0; i < node.size(); i++) {
        if (i > 0) {
          out.append(',');
        }
        write(node.get(i), out);
      }
      out.append(']');
    } else if (node.isTextual()) {
      string(node.asText(), out);
    } else if (node.isNumber()) {
      out.append(number(node));
    } else if (node.isBoolean()) {
      out.append(node.asBoolean());
    } else {
      out.append("null");
    }
  }

  private static String number(JsonNode node) {
    if (node.isIntegralNumber()) {
      return node.bigIntegerValue().toString();
    }
    BigDecimal value = node.decimalValue();
    if (value.signum() == 0) {
      return "0";
    }
    return value.stripTrailingZeros().toPlainString();
  }

  private static void string(String text, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
