package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * NFR-19: the same input, versions and (stand-in) model answers give the same result apart from IDs
 * and timestamps. Checked on the whole result, memo included.
 */
@Requirement("NFR-19")
class DeterminismIT extends ApiAnalysisIT {

  private static final Set<String> VOLATILE =
      Set.of(
          "analysisId",
          "createdAt",
          "completedAt",
          "expiresAt",
          "generatedAt",
          "reviewTicket",
          "envelope",
          "reviewItemId",
          "citationId",
          "checkedAt",
          "verifiedAt",
          "at");

  private static JsonNode stable(JsonNode node) {
    if (node.isObject()) {
      ObjectNode out = ((ObjectNode) node).objectNode();
      Iterator<Map.Entry<String, JsonNode>> it = node.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        boolean memoId = e.getKey().equals("id") && node.has("sections");
        if (!VOLATILE.contains(e.getKey()) && !memoId) {
          out.set(e.getKey(), stable(e.getValue()));
        }
      }
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = ((ArrayNode) node).arrayNode();
      node.forEach(n -> out.add(stable(n)));
      return out;
    }
    return node;
  }

  @Test
  void twoRunsOfTheSameContractGiveTheSameResultApartFromIdsAndTimes() {
    String token = worker();

    JsonNode first = postJson(token).getBody();
    JsonNode second = postJson(token).getBody();

    java.util.List<String> differences = new java.util.ArrayList<>();
    diff("", stable(first), stable(second), differences);
    assertThat(differences).as("fields that differ between two runs").isEmpty();
  }

  /** Paths where two trees differ, so a failure names the field (never its text). */
  private static void diff(String path, JsonNode a, JsonNode b, java.util.List<String> out) {
    if (a.isObject() && b.isObject()) {
      java.util.Set<String> names = new java.util.TreeSet<>();
      a.fieldNames().forEachRemaining(names::add);
      b.fieldNames().forEachRemaining(names::add);
      names.forEach(n -> diff(path + "/" + n, a.path(n), b.path(n), out));
    } else if (a.isArray() && b.isArray() && a.size() == b.size()) {
      for (int i = 0; i < a.size(); i++) {
        diff(path + "/" + i, a.get(i), b.get(i), out);
      }
    } else if (!a.equals(b)) {
      out.add(path);
    }
  }
}
