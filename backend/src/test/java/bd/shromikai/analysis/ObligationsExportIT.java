package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.P6Stubs;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-53: the export is per party; every statutory item has a verified citation; an item whose
 * citations all fail verification is dropped.
 */
@Requirement("FR-53")
class ObligationsExportIT extends ApiAnalysisIT {

  @Test
  void statutoryItemsKeepOnlyVerifiedCitationsAndAnUnverifiableOneIsDropped() {
    fake.respondWith(
        "obligations",
        request -> {
          JsonNode candidates = P6Stubs.candidatesOf(request);
          JsonNode first = candidates.get(0);
          String quote = first.path("authoritativeText").asText().substring(0, 60);
          ObjectNode out = TestGateways.MAPPER.createObjectNode();
          out.put("status", "OK").put("reason", "NONE");
          ArrayNode items = out.putArray("items");
          items.add(
              statute(
                  "EMPLOYER",
                  "Give the notice the section requires.",
                  first.path("sectionKey").asText(),
                  quote));
          items.add(
              statute(
                  "WORKER",
                  "An obligation whose quote is not in the section.",
                  first.path("sectionKey").asText(),
                  "this quote does not occur in the section text anywhere"));
          ObjectNode contractOnly = items.addObject();
          contractOnly.put("party", "WORKER").put("obligation", "Report for duty as agreed.");
          contractOnly.putNull("trigger");
          contractOnly.putNull("deadline");
          contractOnly.put("source", "CONTRACT").put("status", "STATED_IN_CONTRACT");
          contractOnly.putArray("clause_ids");
          contractOnly.putArray("citations");
          return out;
        });
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();

    ResponseEntity<JsonNode> response = get(token, "/api/v1/analyses/" + id + "/obligations");

    assertThat(response.getStatusCode())
        .as(String.valueOf(response.getBody()))
        .isEqualTo(HttpStatus.OK);
    JsonNode parties = response.getBody().path("parties");
    assertThat(parties.path("EMPLOYER")).hasSize(1);
    assertThat(parties.path("EMPLOYER").get(0).path("citations")).hasSize(1);
    assertThat(parties.path("WORKER")).hasSize(1); // the unverifiable statutory item is gone
    assertThat(parties.path("WORKER").get(0).path("source").asText()).isEqualTo("CONTRACT");
    assertThat(parties.path("BOTH")).isEmpty();
    assertThat(response.getBody().path("disclaimer").asText()).isNotBlank();
    for (String party : new String[] {"EMPLOYER", "WORKER", "BOTH"}) {
      for (JsonNode item : parties.path(party)) {
        if (!item.path("source").asText().equals("CONTRACT")) {
          assertThat(item.path("citations")).isNotEmpty();
        }
      }
    }
  }

  private static ObjectNode statute(String party, String obligation, String key, String quote) {
    ObjectNode item = TestGateways.MAPPER.createObjectNode();
    item.put("party", party).put("obligation", obligation);
    item.putNull("trigger");
    item.putNull("deadline");
    item.put("source", "STATUTE").put("status", "STATUTORY_NOT_IN_CONTRACT");
    item.putArray("clause_ids");
    item.putArray("citations")
        .addObject()
        .put("section_key", key)
        .put("quote", quote)
        .put("claim", "The section requires this of the party.");
    return item;
  }
}
