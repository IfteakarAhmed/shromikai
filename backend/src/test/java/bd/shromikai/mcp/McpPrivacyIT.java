package bd.shromikai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.text.BanglaNormalizer;
import bd.shromikai.shared.text.OverlapChecker;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * FR-66, R-02, R-03: {@code analyze_employment_contract} returns placeholders, never the original
 * identifiers, and no field except {@code suggested_clause} repeats 8 tokens of the contract.
 */
@Requirement("FR-66")
class McpPrivacyIT extends McpIT {

  private static final String ID = "syn-en-01";

  private JsonNode analyse(String contract) throws Exception {
    var transport =
        HttpClientStreamableHttpTransport.builder(url())
            .endpoint("/mcp")
            .customizeRequest(r -> r.header("Authorization", "Bearer " + key("mcp")))
            .build();
    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(Duration.ofSeconds(150)).build()) {
      client.initialize();
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "analyze_employment_contract",
                  Map.of("text", contract, "side", "WORKER", "output_language", "en")));
      assertThat(result.isError())
          .as("tool error: " + result.structuredContent())
          .isNotEqualTo(true);
      return mapper.valueToTree(result.structuredContent());
    }
  }

  /** Every string in the document with its path. */
  private static void strings(JsonNode node, String path, List<String[]> out) {
    if (node.isTextual()) {
      out.add(new String[] {path, node.asText()});
    } else if (node.isObject()) {
      node.fields().forEachRemaining(e -> strings(e.getValue(), path + "/" + e.getKey(), out));
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        strings(node.get(i), path + "/" + i, out);
      }
    }
  }

  @Test
  void noOriginalIdentifierAppearsInAnyField() throws Exception {
    JsonNode result = analyse(SyntheticContracts.text(ID));

    String all = result.toString();
    String latin = BanglaNormalizer.toLatinDigits(all);
    for (SyntheticContracts.Entity entity : SyntheticContracts.entities(ID)) {
      assertThat(all).as(entity.type()).doesNotContain(entity.value());
      assertThat(latin)
          .as(entity.type() + " (Latin digits)")
          .doesNotContain(BanglaNormalizer.toLatinDigits(entity.value()));
    }
  }

  @Test
  void aPlaceholderTheModelWritesStaysAPlaceholderInTheResult() throws Exception {
    fake.respondWith(
        "risk-analysis",
        request -> {
          JsonNode clauses = bd.shromikai.testsupport.P6Stubs.clausesOf(request);
          java.util.regex.Matcher placeholder =
              java.util.regex.Pattern.compile("\\[[A-Z_]+_[0-9]+\\]").matcher(clauses.toString());
          if (!placeholder.find()) {
            return bd.shromikai.testsupport.TestGateways.json(
                "{\"status\":\"OK\",\"reason\":\"NONE\",\"findings\":[]}");
          }
          return bd.shromikai.testsupport.TestGateways.json(
              "{\"status\":\"OK\",\"reason\":\"NONE\",\"findings\":[{\"title\":\"Party named\","
                  + "\"kind\":\"CONFLICT\",\"severity\":\"HIGH\",\"non_obvious\":false,"
                  + "\"clause_type\":\""
                  + clauses.get(0).path("type").asText()
                  + "\",\"clause_ids\":[\""
                  + clauses.get(0).path("id").asText()
                  + "\"],\"explanation\":\"The contract names "
                  + placeholder.group()
                  + " as a party.\",\"citations\":[],\"confidence\":0.8}]}");
        });

    JsonNode result = analyse(SyntheticContracts.text(ID));

    assertThat(result.toString())
        .containsPattern("The contract names \\[[A-Z_]+_[0-9]+\\] as a party");
    for (SyntheticContracts.Entity entity : SyntheticContracts.entities(ID)) {
      assertThat(result.toString()).doesNotContain(entity.value());
    }
  }

  @Test
  void noFieldExceptSuggestedClauseRepeatsEightTokensOfTheContract() throws Exception {
    String contract = SyntheticContracts.text(ID);
    JsonNode result = analyse(contract);

    OverlapChecker checker = OverlapChecker.of(contract);
    List<String[]> texts = new ArrayList<>();
    strings(result, "", texts);
    assertThat(texts).isNotEmpty();
    for (String[] entry : texts) {
      if (entry[0].endsWith("/suggested_clause")) {
        continue;
      }
      assertThat(checker.overlaps(entry[1])).as(entry[0] + ": " + entry[1]).isFalse();
    }
  }

  @Test
  void suggestedClausesKeepPlaceholdersAndNoOriginalIdentifier() throws Exception {
    JsonNode result = analyse(SyntheticContracts.text(ID));

    for (JsonNode finding : result.path("findings")) {
      JsonNode clause = finding.path("suggested_clause");
      if (clause.isTextual()) {
        for (SyntheticContracts.Entity entity : SyntheticContracts.entities(ID)) {
          assertThat(clause.asText()).doesNotContain(entity.value());
        }
        assertThat(finding.path("verification_status").asText()).isEqualTo("VERIFIED");
      }
    }
  }

  @Test
  void aFieldThatWouldRepeatTheContractIsWithheldAndSaidSo() throws Exception {
    String contract = SyntheticContracts.text(ID);
    fake.respondWith(
        "risk-analysis",
        request -> {
          JsonNode clauses = bd.shromikai.testsupport.P6Stubs.clausesOf(request);
          String copied = clauses.get(0).path("text").asText();
          String eightWords =
              String.join(" ", java.util.Arrays.copyOfRange(copied.split("\\s+"), 0, 10));
          return bd.shromikai.testsupport.TestGateways.json(
              "{\"status\":\"OK\",\"reason\":\"NONE\",\"findings\":[{\"title\":\"Copied clause\","
                  + "\"kind\":\"CONFLICT\",\"severity\":\"HIGH\",\"non_obvious\":false,"
                  + "\"clause_type\":\""
                  + clauses.get(0).path("type").asText()
                  + "\",\"clause_ids\":[\""
                  + clauses.get(0).path("id").asText()
                  + "\"],\"explanation\":\""
                  + eightWords.replace("\"", "'").replace("\\", "")
                  + "\",\"citations\":[],\"confidence\":0.8}]}");
        });

    JsonNode result = analyse(contract);

    OverlapChecker checker = OverlapChecker.of(contract);
    List<String[]> texts = new ArrayList<>();
    strings(result, "", texts);
    for (String[] entry : texts) {
      if (!entry[0].endsWith("/suggested_clause")) {
        assertThat(checker.overlaps(entry[1])).as(entry[0]).isFalse();
      }
    }
    assertThat(result.path("findings").get(0).path("explanation").asText()).contains("withheld");
    assertThat(result.path("warnings").toString()).contains("text-withheld");
  }
}
