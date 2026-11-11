package bd.shromikai.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a JSON response and reports every object that looks like a citation (it has a {@code
 * sectionKey} and a {@code quote}) but does not carry {@code verification.status = VERIFIED} (R-01,
 * FR-47, M-01). Compare mode's baseline column is the one allowed exception; no response of P6 has
 * one.
 */
public final class VerifiedOnlyScanner {

  private VerifiedOnlyScanner() {}

  public static List<String> violations(JsonNode root) {
    List<String> out = new ArrayList<>();
    walk(root, "$", out);
    return out;
  }

  public static int citations(JsonNode root) {
    int[] count = {0};
    count(root, count);
    return count[0];
  }

  private static void walk(JsonNode node, String path, List<String> out) {
    if (node.isObject()) {
      if (node.has("sectionKey") && node.has("quote")) {
        String status = node.path("verification").path("status").asText("");
        if (!"VERIFIED".equals(status)) {
          out.add(
              path
                  + " has sectionKey "
                  + node.path("sectionKey").asText()
                  + " with status '"
                  + status
                  + "'");
        }
      }
      node.properties().forEach(e -> walk(e.getValue(), path + "." + e.getKey(), out));
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), path + "[" + i + "]", out);
      }
    }
  }

  private static void count(JsonNode node, int[] count) {
    if (node.isObject()) {
      if (node.has("sectionKey") && node.has("quote")) {
        count[0]++;
      }
      node.forEach(child -> count(child, count));
    } else if (node.isArray()) {
      node.forEach(child -> count(child, count));
    }
  }
}
