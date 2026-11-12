package bd.shromikai.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * FR-64: the official MCP Java SDK client connects over Streamable HTTP, lists the five tools and
 * calls them. Model answers are scripted stand-ins, not recorded model output, so the VERIFIED
 * result proves the wiring of the three checks, not the quality of a model.
 */
@Requirement("FR-64")
class McpServerIT extends McpIT {

  @org.springframework.beans.factory.annotation.Autowired bd.shromikai.analysis.JobStore jobStore;

  private static final String QUOTE = "A probation period shall not exceed six months.";
  private static final String CLAIM =
      "A probation period longer than six months is not allowed under this section.";

  private McpSyncClient connect(String key) {
    var transport =
        HttpClientStreamableHttpTransport.builder(url())
            .endpoint("/mcp")
            .customizeRequest(request -> request.header("Authorization", "Bearer " + key))
            .build();
    McpSyncClient client =
        McpClient.sync(transport).requestTimeout(Duration.ofSeconds(150)).build();
    client.initialize();
    return client;
  }

  private JsonNode call(McpSyncClient client, String tool, Map<String, Object> args)
      throws Exception {
    McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, args));
    assertThat(result.content()).isNotEmpty();
    JsonNode structured = mapper.valueToTree(result.structuredContent());
    JsonNode text = mapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
    assertThat(structured).as("structuredContent equals the text item").isEqualTo(text);
    return structured.deepCopy();
  }

  @Test
  void theSdkClientListsTheFiveToolsTheResourceTemplateAndThePrompt() {
    try (McpSyncClient client = connect(key("mcp"))) {
      assertThat(client.listTools().tools())
          .extracting(McpSchema.Tool::name)
          .containsExactly(
              "search_bd_law",
              "get_law_section",
              "verify_legal_citation",
              "analyze_employment_contract",
              "list_jurisdiction_packs");
      assertThat(client.listTools().tools())
          .allSatisfy(
              tool -> {
                assertThat(tool.description()).hasSizeGreaterThan(80);
                assertThat(tool.inputSchema()).isNotNull();
                assertThat(tool.outputSchema()).isNotNull();
              });
      assertThat(client.listResourceTemplates().resourceTemplates())
          .extracting(McpSchema.ResourceTemplate::uriTemplate)
          .containsExactly("law://bd/{act_code}/{section_no}");
      assertThat(client.listPrompts().prompts())
          .extracting(McpSchema.Prompt::name)
          .containsExactly("review-employment-contract");
    }
  }

  @Test
  void aRealSectionWithASupportingClaimIsVerifiedAsExternal() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode result =
          call(
              client,
              "verify_legal_citation",
              Map.of(
                  "act_code",
                  FictionalCorpus.ACT,
                  "section_no",
                  "1",
                  "quoted_text",
                  QUOTE,
                  "claim",
                  CLAIM));

      assertThat(result.path("status").asText()).isEqualTo("VERIFIED");
      assertThat(result.path("checks").path("existence").path("outcome").asText())
          .isEqualTo("PASS");
      assertThat(result.path("checks").path("quote_match").path("outcome").asText())
          .isEqualTo("PASS");
      assertThat(result.path("checks").path("support").path("outcome").asText()).isEqualTo("PASS");
      assertThat(result.path("reason_codes")).isEmpty();
      assertThat(result.path("verified_citation").path("section_key").asText())
          .startsWith(FictionalCorpus.ACT + ":1@");
      assertThat(result.path("verified_citation").path("quote").asText()).contains("six months");
      assertThat(result.path("corpus_release").asText()).isNotBlank();
      assertThat(result.path("disclaimer").asText()).contains("আইনি পরামর্শ নয়");
    }
  }

  @Test
  void aFabricatedSectionIsRejectedWithoutACitation() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode result =
          call(
              client,
              "verify_legal_citation",
              Map.of(
                  "act_code",
                  FictionalCorpus.ACT,
                  "section_no",
                  "99",
                  "quoted_text",
                  QUOTE,
                  "claim",
                  CLAIM));

      assertThat(result.path("status").asText()).isEqualTo("REJECTED");
      assertThat(result.path("reason_codes"))
          .extracting(JsonNode::asText)
          .contains("SECTION_NOT_FOUND");
      assertThat(result.path("verified_citation").isNull()).isTrue();
    }
  }

  @Test
  void aQuoteThatIsNotInTheSectionIsRejected() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode result =
          call(
              client,
              "verify_legal_citation",
              Map.of(
                  "act_code",
                  FictionalCorpus.ACT,
                  "section_no",
                  "1",
                  "quoted_text",
                  "Workers may be dismissed at any time without any notice at all.",
                  "claim",
                  CLAIM));

      assertThat(result.path("status").asText()).isEqualTo("REJECTED");
      assertThat(result.path("verified_citation").isNull()).isTrue();
    }
  }

  @Test
  void getLawSectionReturnsTheCurrentTextWithVersionAndSource() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode result =
          call(
              client,
              "get_law_section",
              Map.of("act_code", FictionalCorpus.ACT, "section_no", "3"));

      assertThat(result.path("section_key").asText()).startsWith(FictionalCorpus.ACT + ":3@");
      assertThat(
              result.path("text_en").asText().isBlank()
                  ? result.path("text_bn").asText()
                  : result.path("text_en").asText())
          .contains("eight hours");
      assertThat(result.path("version_label").asText()).isNotBlank();
      assertThat(result.path("source_url").asText()).isNotBlank();
      assertThat(result.path("is_current").asBoolean()).isTrue();
    }
  }

  @Test
  void getLawSectionForAnUnknownSectionIsAToolErrorWithAProblem() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "get_law_section", Map.of("act_code", FictionalCorpus.ACT, "section_no", "404")));

      JsonNode problem = problemOf(result);
      assertThat(problem.path("code").asText()).isEqualTo("not-found");
      assertThat(problem.path("status").asInt()).isEqualTo(404);
    }
  }

  @Test
  void searchReturnsSectionKeysWithSourceUrls() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode result =
          call(client, "search_bd_law", Map.of("query", "probation period", "top_k", 5));

      assertThat(result.path("results")).isNotEmpty();
      assertThat(result.path("results").get(0).path("section_key").asText()).contains(":");
      assertThat(result.path("results").get(0).path("source_url").asText()).isNotBlank();
      assertThat(result.path("results").size()).isLessThanOrEqualTo(5);
    }
  }

  @Test
  void invalidArgumentsAreAValidationProblemNotACrash() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "verify_legal_citation",
                  Map.of(
                      "act_code", FictionalCorpus.ACT,
                      "section_no", "1",
                      "quoted_text", "too short",
                      "claim", "short")));

      assertThat(problemOf(result).path("code").asText()).isEqualTo("validation-failed");
    }
  }

  @Test
  void listJurisdictionPacksReportsCoverage() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode result = call(client, "list_jurisdiction_packs", Map.of());

      assertThat(result.path("packs")).isNotEmpty();
      JsonNode bd = result.path("packs").get(0);
      assertThat(bd.path("code").asText()).isEqualTo("BD");
      assertThat(bd.path("acts")).isNotEmpty();
      assertThat(bd.path("acts").get(0).path("section_count").asInt()).isPositive();
      assertThat(bd.has("active_rules")).isTrue();
      assertThat(bd.has("reviewed_ilo_mappings")).isTrue();
    }
  }

  @Test
  void theLawResourceAndThePromptTemplateWork() {
    try (McpSyncClient client = connect(key("mcp"))) {
      McpSchema.ReadResourceResult resource =
          client.readResource(
              new McpSchema.ReadResourceRequest("law://bd/" + FictionalCorpus.ACT + "/2"));
      String markdown = ((McpSchema.TextResourceContents) resource.contents().get(0)).text();
      assertThat(markdown).contains("wages").contains("Source");
      assertThat(resource.contents().get(0).mimeType()).isEqualTo("text/markdown");

      McpSchema.GetPromptResult prompt =
          client.getPrompt(
              new McpSchema.GetPromptRequest(
                  "review-employment-contract", Map.of("side", "WORKER", "language", "bn")));
      String text = ((McpSchema.TextContent) prompt.messages().get(0).content()).text();
      assertThat(text)
          .contains("analyze_employment_contract")
          .contains("verify_legal_citation")
          .contains("WORKER")
          .containsIgnoringCase("uncertain");
    }
  }

  @Test
  void analyzeEmploymentContractReturnsVerifiedFindingsAndNoStoredAnalysis() throws Exception {
    try (McpSyncClient client = connect(key("mcp", "analyses"))) {
      JsonNode result =
          call(
              client,
              "analyze_employment_contract",
              Map.of(
                  "text", SyntheticContracts.text("syn-en-01"),
                  "side", "WORKER",
                  "output_language", "en"));

      assertThat(result.path("analysis_id").asText()).isNotBlank();
      assertThat(result.path("findings")).isNotEmpty();
      for (JsonNode finding : result.path("findings")) {
        assertThat(finding.path("verification_status").asText())
            .isIn("VERIFIED", "UNCERTAIN", "POLICY_ONLY");
        if (!finding.path("verification_status").asText().equals("VERIFIED")) {
          assertThat(finding.path("citations")).isEmpty();
        }
      }
      assertThat(result.path("summary").has("verified_findings")).isTrue();
      assertThat(result.path("summary").path("findings_by_severity").has("HIGH"))
          .as("severity keys keep their names")
          .isTrue();
      assertThat(result.path("provenance").path("corpus_release").asText()).isNotBlank();
      assertThat(result.path("disclaimer").asText()).contains("not legal advice");
      assertThat(jobStore.size()).as("the finished job is removed (FR-66)").isZero();
    }
  }

  @Test
  void everyCallWritesAUsageEventOfTheKeysOrganisation() {
    java.util.UUID org = organisation("Usage org");
    String key = keyFor(org, "mcp");
    try (McpSyncClient client = connect(key)) {
      client.callTool(new McpSchema.CallToolRequest("list_jurisdiction_packs", Map.of()));
      client.callTool(new McpSchema.CallToolRequest("list_jurisdiction_packs", Map.of()));
    }

    assertThat(
            jdbc.sql(
                    "select count(*) from usage_event where org_id = :o and kind = 'MCP_CALL'"
                        + " and api_key_id is not null")
                .param("o", org)
                .query(Long.class)
                .single())
        .isEqualTo(2L);
  }

  /**
   * A strict client validates structuredContent against the tool's outputSchema. A problem document
   * is not a success shape, so an error result must not carry one (otherwise the client reports
   * "required property" instead of the real error). Checked for every tool that can fail.
   */
  @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"get_law_section", "verify_legal_citation", "search_bd_law"})
  void aFailedCallHasNoStructuredContentSoItCannotMismatchTheOutputSchema(String tool)
      throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      client.listTools(); // a strict client caches the output schemas first
      McpSchema.CallToolResult result =
          client.callTool(new McpSchema.CallToolRequest(tool, Map.of("unexpected", "argument")));

      JsonNode problem = problemOf(result);
      assertThat(problem.path("code").asText()).isEqualTo("validation-failed");
      assertThat(problem.path("status").asInt()).isEqualTo(400);
      assertThat(problem.path("disclaimer").asText()).isNotBlank();
    }
  }

  @Test
  void aNumericSectionNumberIsReadAsItsText() throws Exception {
    try (McpSyncClient client = connect(key("mcp"))) {
      JsonNode viaNumber =
          call(client, "get_law_section", Map.of("act_code", FictionalCorpus.ACT, "section_no", 1));
      JsonNode viaText =
          call(
              client,
              "get_law_section",
              Map.of("act_code", FictionalCorpus.ACT, "section_no", "1"));

      assertThat(viaNumber.path("section_key").asText())
          .isEqualTo(viaText.path("section_key").asText());
    }
  }

  @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
  @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5", "-3", "true", "99999999999"})
  void aSectionNumberThatIsNotAWholeNonNegativeNumberIsStillRefused(String json) throws Exception {
    Object value = mapper.readValue(json, Object.class);
    try (McpSyncClient client = connect(key("mcp"))) {
      McpSchema.CallToolResult result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "get_law_section", Map.of("act_code", FictionalCorpus.ACT, "section_no", value)));

      assertThat(problemOf(result).path("code").asText()).isEqualTo("validation-failed");
    }
  }
}
