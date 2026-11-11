package bd.shromikai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.fake.RecordedCall;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * FR-64, NFR-06, architecture §10.7: a claim with PII canaries reaches the model stubs only as
 * placeholders, and {@code search_bd_law} makes no model call at all (the guard's own blocking is
 * proved in {@code EgressPlumbingIT}, which this class complements for the MCP paths).
 */
@Requirement("FR-64")
@Requirement("NFR-06")
class NoPiiLeavesTheBoxIT extends McpIT {

  private static final String ID = "syn-en-01";

  private McpSyncClient connect() {
    var transport =
        HttpClientStreamableHttpTransport.builder(url())
            .endpoint("/mcp")
            .customizeRequest(r -> r.header("Authorization", "Bearer " + key("mcp")))
            .build();
    McpSyncClient client = McpClient.sync(transport).build();
    client.initialize();
    return client;
  }

  private List<String> canaries() {
    return SyntheticContracts.values(ID, "PERSON", "NID", "PHONE", "EMAIL");
  }

  @Test
  void aVerifyClaimWithCanariesReachesTheModelsOnlyAsPlaceholders() {
    List<String> canaries = canaries();
    assertThat(canaries).hasSizeGreaterThanOrEqualTo(3);
    String claim =
        SyntheticContracts.values(ID, "PERSON").get(0)
            + " (NID "
            + SyntheticContracts.values(ID, "NID").get(0)
            + ", phone "
            + SyntheticContracts.values(ID, "PHONE").get(0)
            + ", email "
            + SyntheticContracts.values(ID, "EMAIL").get(0)
            + ") was kept on probation for longer than six months, which the section forbids.";

    try (McpSyncClient client = connect()) {
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "verify_legal_citation",
                  Map.of(
                      "act_code",
                      FictionalCorpus.ACT,
                      "section_no",
                      "1",
                      "quoted_text",
                      "A probation period shall not exceed six months.",
                      "claim",
                      claim)));
      assertThat(result.isError()).isNotEqualTo(true);
    }

    List<RecordedCall> calls = fake.calls();
    List<RecordedCall> support =
        calls.stream().filter(c -> c.promptId().startsWith("verify-support")).toList();
    assertThat(support).as("the support check ran").isNotEmpty();
    for (RecordedCall call : support) {
      String payload = call.request().system() + "\n" + call.request().user().reveal();
      for (String canary : canaries) {
        assertThat(payload).as("canary " + canary).doesNotContain(canary);
      }
      assertThat(payload).contains("[PERSON_1]");
    }
    for (RecordedCall call : calls) {
      String payload = call.request().system() + "\n" + call.request().user().reveal();
      if (!call.promptId().equals("pii-extract")) { // the local model sees the raw claim by design
        for (String canary : canaries) {
          assertThat(payload).as(call.promptId()).doesNotContain(canary);
        }
      }
    }
  }

  @Test
  void aSearchWithCanariesMakesNoModelCallAndRewritesNothing() {
    String query = canaries().get(0) + " " + canaries().get(1) + " probation period";

    try (McpSyncClient client = connect()) {
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest("search_bd_law", Map.of("query", query, "top_k", 5)));
      assertThat(result.isError()).isNotEqualTo(true);
    }

    assertThat(fake.calls()).as("no chat call of any kind, so no cloud call").isEmpty();
  }

  @Test
  void aClaimIsNotSentWhenTheLocalModelIsDown() {
    fake.failWith("pii-extract", bd.shromikai.shared.ai.fake.FailureMode.SERVER_ERROR);

    try (McpSyncClient client = connect()) {
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "verify_legal_citation",
                  Map.of(
                      "act_code", FictionalCorpus.ACT,
                      "section_no", "1",
                      "quoted_text", "A probation period shall not exceed six months.",
                      "claim",
                          "A claim of enough length that the local model must first redact.")));

      assertThat(result.isError()).isTrue();
      assertThat(mapper.valueToTree(result.structuredContent()).path("code").asText())
          .isEqualTo("local-model-unavailable");
    }
    assertThat(fake.calls()).noneMatch(c -> c.promptId().startsWith("verify-support"));
  }
}
