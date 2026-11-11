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
 * and cannot use B's playbook for an analysis of A. (API keys, usage events and MCP are P8.)
 */
@Requirement("NFR-12")
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
}
