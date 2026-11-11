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

    assertThat(stable(second)).isEqualTo(stable(first));
  }
}
