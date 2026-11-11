package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * NFR-12, T-26: an HR user of organisation A gets 404 on organisation B's playbooks and analyses,
 * and cannot use B's playbook for an analysis of A. P8 adds API keys, usage events and analyses
 * started through MCP (T-26).
 */
@Requirement("NFR-12")
@org.springframework.test.context.ActiveProfiles({"test", "mcp"})
class CrossOrganisationIsolationIT extends ApiAnalysisIT {

  @Autowired PasswordEncoder encoder;

  private UUID orgA;
  private UUID orgB;
  private String hrA;
  private String hrB;

  @BeforeEach
  void organisations() {
    orgA = org("Org A");
    orgB = org("Org B");
    hrA = hr(orgA);
    hrB = hr(orgB);
  }

  @AfterEach
  void removeOrganisations() {
    jdbc.sql("delete from playbook").update();
    jdbc.sql("delete from app_user where org_id in (:a, :b)")
        .param("a", orgA)
        .param("b", orgB)
        .update();
    jdbc.sql("delete from organisation where id in (:a, :b)")
        .param("a", orgA)
        .param("b", orgB)
        .update();
  }

  private UUID org(String name) {
    return jdbc.sql(
            "insert into organisation (name, kind, plan) values (:n, 'HR', 'TEAM') returning id")
        .param("n", name)
        .query(UUID.class)
        .single();
  }

  private String hr(UUID org) {
    UUID user =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            IdentityFixtures.uniqueEmail("hr"),
            IdentityFixtures.PASSWORD,
            Role.HR,
            false);
    jdbc.sql("update app_user set org_id = :org where id = :id")
        .param("org", org)
        .param("id", user)
        .update();
    return jwt.issueUser(user, Role.HR, org, false).value();
  }

  private String createPlaybook(String token) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body =
        """
        {"name":"Standard","side":"EMPLOYER","rules":[{"clauseType":"PROBATION",
         "fact":"PROBATION_LENGTH","comparator":"MAX","value":3,"unit":"MONTHS","severity":"MEDIUM",
         "messageBn":"বার্তা","messageEn":"Probation is at most 3 months.","statuteSectionKey":null}]}
        """;
    return http.exchange(
            "/api/v1/playbooks", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class)
        .getBody()
        .path("id")
        .asText();
  }

  @Test
  void anHrUserOfAnotherOrganisationGets404OnAnalysesAndPlaybooks() {
    String analysisId = postJson(hrA).getBody().path("analysisId").asText();
    String playbookId = createPlaybook(hrA);

    assertThat(get(hrB, "/api/v1/analyses/" + analysisId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(hrB, "/api/v1/analyses/" + analysisId + "/trace").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(hrB, "/api/v1/playbooks/" + playbookId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(hrB, "/api/v1/playbooks").getBody().path("items")).isEmpty();
    assertThat(get(hrA, "/api/v1/playbooks/" + playbookId).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void aPlaybookOfAnotherOrganisationCannotBeUsedForAnAnalysis() {
    String playbookOfA = createPlaybook(hrA);

    ResponseEntity<JsonNode> response =
        postJson(
            hrB,
            java.util.Map.of(
                "text",
                text(),
                "side",
                "EMPLOYER",
                "outputLanguage",
                "en",
                "playbookId",
                playbookOfA));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aWorkerCannotNameAPlaybookAtAll() {
    String playbookOfA = createPlaybook(hrA);

    ResponseEntity<JsonNode> response =
        postJson(
            worker(),
            java.util.Map.of(
                "text",
                text(),
                "side",
                "EMPLOYER",
                "outputLanguage",
                "en",
                "playbookId",
                playbookOfA));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void anHrUserCanUseTheOwnPlaybookAndGetsPolicyFindingsSeparateFromLegalOnes() {
    String playbookId = createPlaybook(hrA);

    ResponseEntity<JsonNode> response =
        postJson(
            hrA,
            java.util.Map.of(
                "text",
                text(),
                "side",
                "EMPLOYER",
                "outputLanguage",
                "en",
                "playbookId",
                playbookId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode finding : response.getBody().path("findings")) {
      boolean policy = finding.path("source").asText().equals("PLAYBOOK");
      assertThat(finding.path("verificationStatus").asText().equals("POLICY_ONLY"))
          .isEqualTo(policy);
      if (policy) {
        assertThat(finding.path("citations")).isEmpty();
      }
    }
  }

  // ---------------------------------------------------------------------------- P8

  @Autowired bd.shromikai.auth.ApiKeyService keys;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

  private String admin(UUID org) {
    UUID user =
        IdentityFixtures.insertUser(
            jdbc,
            encoder,
            IdentityFixtures.uniqueEmail("admin"),
            IdentityFixtures.PASSWORD,
            Role.ADMIN,
            false);
    jdbc.sql("update app_user set org_id = :org where id = :id")
        .param("org", org)
        .param("id", user)
        .update();
    return jwt.issueUser(user, Role.ADMIN, org, false).value();
  }

  private ResponseEntity<JsonNode> send(HttpMethod method, String token, String path, Object body) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  @Test
  void anAdminOfAnotherOrganisationSeesNeitherTheKeysNorCanRevokeThem() {
    String adminA = admin(orgA);
    String adminB = admin(orgB);
    JsonNode created =
        send(
                HttpMethod.POST,
                adminA,
                "/api/v1/admin/api-keys",
                java.util.Map.of("name", "A key", "scopes", java.util.List.of("mcp")))
            .getBody();
    String id = created.path("id").asText();

    assertThat(send(HttpMethod.GET, adminB, "/api/v1/admin/api-keys", null).getBody().path("items"))
        .isEmpty();
    assertThat(
            send(HttpMethod.DELETE, adminB, "/api/v1/admin/api-keys/" + id, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            send(HttpMethod.GET, adminB, "/api/v1/admin/api-keys?orgId=" + orgA, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbc.sql("select revoked_at is null from api_key where id = cast(:id as uuid)")
                .param("id", id)
                .query(Boolean.class)
                .single())
        .as("B's admin did not revoke A's key")
        .isTrue();
    assertThat(send(HttpMethod.GET, adminA, "/api/v1/admin/api-keys", null).getBody().path("items"))
        .hasSize(1);
  }

  private JsonNode mcpCall(String key, String tool, java.util.Map<String, Object> arguments)
      throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(key);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
    ResponseEntity<String> init =
        http.exchange(
            "/mcp",
            HttpMethod.POST,
            new HttpEntity<>(
                java.util.Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    1,
                    "method",
                    "initialize",
                    "params",
                    java.util.Map.of(
                        "protocolVersion",
                        "2025-06-18",
                        "capabilities",
                        java.util.Map.of(),
                        "clientInfo",
                        java.util.Map.of("name", "t", "version", "1"))),
                headers),
            String.class);
    headers.set("Mcp-Session-Id", init.getHeaders().getFirst("Mcp-Session-Id"));
    String body =
        http.exchange(
                "/mcp",
                HttpMethod.POST,
                new HttpEntity<>(
                    java.util.Map.of(
                        "jsonrpc",
                        "2.0",
                        "id",
                        2,
                        "method",
                        "tools/call",
                        "params",
                        java.util.Map.of("name", tool, "arguments", arguments)),
                    headers),
                String.class)
            .getBody();
    int data = body.indexOf("data:");
    return mapper.readTree(data >= 0 ? body.substring(data + 5).trim() : body).path("result");
  }

  @Test
  void usageEventsAndMcpStartedAnalysesOfOneOrganisationAreInvisibleToTheOther() throws Exception {
    String keyB = keys.create(orgB, "B key", java.util.List.of("mcp"), null).key();

    JsonNode result =
        mcpCall(
            keyB,
            "analyze_employment_contract",
            java.util.Map.of("text", text(), "side", "WORKER", "output_language", "en"));
    String analysisId = result.path("structuredContent").path("analysis_id").asText();
    assertThat(analysisId).isNotBlank();

    assertThat(
            jdbc.sql("select count(*) from usage_event where org_id = :o and kind = 'MCP_CALL'")
                .param("o", orgB)
                .query(Long.class)
                .single())
        .isEqualTo(1L);
    assertThat(
            jdbc.sql("select count(*) from usage_event where org_id = :o")
                .param("o", orgA)
                .query(Long.class)
                .single())
        .isZero();
    for (String hr : java.util.List.of(hrA, hrB)) {
      assertThat(get(hr, "/api/v1/analyses/" + analysisId).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(get(hr, "/api/v1/analyses/" + analysisId + "/trace").getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
    assertThat(
            jdbc.sql(
                    "select org_id from analysis_run where id = cast(:id as uuid)"
                        + " and client_kind = 'MCP'")
                .param("id", analysisId)
                .query(UUID.class)
                .optional())
        .contains(orgB);
  }
}
