package bd.shromikai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.VerifiedOnlyScanner;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FR-47, FR-66, R-01: the MCP analysis response carries only verified citations. */
@Requirement("FR-47")
@Requirement("FR-66")
class McpVerifiedOnlyIT extends McpIT {

  @Test
  void theAnalyzeToolReturnsOnlyVerifiedCitations() throws Exception {
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
                  Map.of(
                      "text",
                      SyntheticContracts.text("syn-en-01"),
                      "side",
                      "WORKER",
                      "output_language",
                      "en")));
      assertThat(result.isError()).isNotEqualTo(true);
      JsonNode body = mapper.valueToTree(result.structuredContent());

      assertThat(VerifiedOnlyScanner.violations(body)).isEmpty();
      assertThat(VerifiedOnlyScanner.citations(body)).as("the scan saw citations").isPositive();
    }
  }
}
