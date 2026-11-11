package bd.shromikai.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a JSON response and reports every object that looks like a citation (it has a {@code
 * sectionKey} and a {@code quote}) but does not carry {@code verification.status = VERIFIED} (R-01,
 * FR-47, M-01). Compare mode's baseline column is the one allowed exception, through {@link
 * #violationsAllowingLabelledBaselineClaims} only (ADR-0022).
 */
public final class VerifiedOnlyScanner {

  private VerifiedOnlyScanner() {}

  /** A citation by shape: a section key (REST: camelCase, MCP: snake_case) and a quote. */
  private static boolean isCitation(JsonNode node) {
    return (node.has("sectionKey") || node.has("section_key")) && node.has("quote");
  }

  public static List<String> violations(JsonNode root) {
    List<String> out = new ArrayList<>();
    walk(root, "$", out);
    return out;
  }

  /**
   * Like {@link #violations}, for a compare result (ADR-0022): a baseline claim is allowed to be
   * {@code REJECTED} or {@code UNCERTAIN} only as a {@code claimed} object whose parent carries the
   * verifier's {@code verification.status}, and never with a {@code citation} of its own. Every
   * other object is held to the strict rule.
   */
  public static List<String> violationsAllowingLabelledBaselineClaims(JsonNode root) {
    List<String> out = new ArrayList<>();
    walkCompare(root, "$", out);
    return out;
  }

  private static void walkCompare(JsonNode node, String path, List<String> out) {
    if (node.isObject()) {
      boolean labelled =
          node.has("claimed")
              && node.has("verification")
              && !node.has("citation")
              && !node.has("sectionKey")
              && java.util.Set.of("VERIFIED", "UNCERTAIN", "REJECTED")
                  .contains(node.path("verification").path("status").asText(""));
      if (isCitation(node)) {
        String status = node.path("verification").path("status").asText("");
        if (!"VERIFIED".equals(status)) {
          out.add(
              path
                  + " has sectionKey "
                  + (node.has("sectionKey") ? node.path("sectionKey") : node.path("section_key"))
                      .asText());
        }
      }
      node.properties()
          .forEach(
              e -> {
                if (labelled && e.getKey().equals("claimed")) {
                  return;
                }
                walkCompare(e.getValue(), path + "." + e.getKey(), out);
              });
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walkCompare(node.get(i), path + "[" + i + "]", out);
      }
    }
  }

  public static int citations(JsonNode root) {
    int[] count = {0};
    count(root, count);
    return count[0];
  }

  private static void walk(JsonNode node, String path, List<String> out) {
    if (node.isObject()) {
      if (isCitation(node)) {
        String status = node.path("verification").path("status").asText("");
        if (!"VERIFIED".equals(status)) {
          out.add(
              path
                  + " has sectionKey "
                  + (node.has("sectionKey") ? node.path("sectionKey") : node.path("section_key"))
                      .asText()
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
      if (isCitation(node)) {
        count[0]++;
      }
      node.forEach(child -> count(child, count));
    } else if (node.isArray()) {
      node.forEach(child -> count(child, count));
    }
  }
}
