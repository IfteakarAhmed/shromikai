package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.testsupport.VerifiedOnlyScanner;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * FR-47, R-01, M-01 = 0 for the P7 and P8 responses: the public result with its memo and
 * obligations checklist, a playbook run, and a compare result (where the baseline's rejected claims
 * are labelled non-citations, ADR-0022). The MCP response is scanned in {@code McpVerifiedOnlyIT}.
 * This is the public-API companion of {@code AllResponsesCarryOnlyVerifiedCitationsIT}, which scans
 * the dev endpoints (a test class has one base class).
 */
@Requirement("FR-47")
class ApiResponsesCarryOnlyVerifiedCitationsIT extends ApiAnalysisIT {

  private static final String BASELINE =
      """
      {"status":"OK","reason":"NONE","findings":[
       {"title":"Dismissal needs notice","severity":"HIGH",
        "explanation":"The contract allows dismissal without the notice the law requires.",
        "citations":[
         {"section_key":"XV-LAB:5@original",
          "quote":"shall give a worker written notice of dismissal at least fourteen days",
          "claim":"An employer must give written notice before dismissal."},
         {"section_key":"XV-LAB:999@original",
          "quote":"an invented section that says whatever the model wants",
          "claim":"The worker may never be dismissed."}]}]}
      """;

  @Autowired PasswordEncoder encoder;
  private UUID org;

  @AfterEach
  void removeOrganisation() {
    if (org != null) {
      jdbc.sql("delete from playbook").update();
      jdbc.sql("delete from app_user where org_id = :o").param("o", org).update();
      jdbc.sql("delete from organisation where id = :o").param("o", org).update();
    }
  }

  @Test
  void theResultWithMemoAndObligationsHoldsOnlyVerifiedCitations() {
    ResponseEntity<JsonNode> response = postJson(worker());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode result = response.getBody();
    assertThat(result.path("memo").path("sections")).isNotEmpty();
    assertThat(result.path("obligations")).as("the checklist is in the result").isNotEmpty();
    assertThat(VerifiedOnlyScanner.violations(result)).isEmpty();
    assertThat(VerifiedOnlyScanner.citations(result)).as("the scan saw citations").isPositive();
  }

  @Test
  void aPlaybookRunHoldsOnlyVerifiedCitationsAndPolicyFindingsCarryNone() {
    org =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values ('R01 Org', 'HR', 'TEAM')"
                    + " returning id")
            .query(UUID.class)
            .single();
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
    String hr = jwt.issueUser(user, Role.HR, org, false).value();
    HttpHeaders headers = bearer(hr);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String playbook =
        """
        {"name":"Standard","side":"EMPLOYER","rules":[{"clauseType":"PROBATION",
         "fact":"PROBATION_LENGTH","comparator":"MAX","value":3,"unit":"MONTHS","severity":"MEDIUM",
         "messageBn":"বার্তা","messageEn":"Probation is at most 3 months.","statuteSectionKey":null}]}
        """;
    String playbookId =
        http.exchange(
                "/api/v1/playbooks",
                HttpMethod.POST,
                new HttpEntity<>(playbook, headers),
                JsonNode.class)
            .getBody()
            .path("id")
            .asText();

    ResponseEntity<JsonNode> response =
        postJson(
            hr,
            Map.of(
                "text",
                text(),
                "side",
                "EMPLOYER",
                "outputLanguage",
                "en",
                "playbookId",
                playbookId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode result = response.getBody();
    assertThat(VerifiedOnlyScanner.violations(result)).isEmpty();
    assertThat(result.path("findings"))
        .anySatisfy(f -> assertThat(f.path("source").asText()).isEqualTo("PLAYBOOK"));
  }

  @Test
  void aCompareResultShowsRejectedBaselineClaimsOnlyAsLabelledNonCitations() throws Exception {
    fake.respondWith("baseline", request -> TestGateways.json(BASELINE));
    String token = worker();
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String id =
        http.exchange(
                "/api/v1/compare",
                HttpMethod.POST,
                new HttpEntity<>(
                    Map.of("text", text(), "side", "WORKER", "outputLanguage", "en"), headers),
                JsonNode.class)
            .getBody()
            .path("compareId")
            .asText();
    JsonNode result = null;
    for (int i = 0; i < 300 && result == null; i++) {
      ResponseEntity<JsonNode> r = get(token, "/api/v1/compare/" + id);
      if (r.getStatusCode() == HttpStatus.OK) {
        result = r.getBody();
      } else {
        Thread.sleep(100);
      }
    }
    assertThat(result).as("compare finished").isNotNull();

    assertThat(result.path("summary").path("baselineRejected").asInt()).isPositive();
    // The ShromikAI side obeys the strict rule; the baseline only the labelled exemption.
    assertThat(VerifiedOnlyScanner.violations(result.path("shromikai"))).isEmpty();
    assertThat(VerifiedOnlyScanner.violationsAllowingLabelledBaselineClaims(result)).isEmpty();
    // The exemption is narrow: without it the rejected claim is found, so the scan has teeth.
    assertThat(VerifiedOnlyScanner.violations(result.path("baseline"))).isNotEmpty();
    for (JsonNode finding : result.path("baseline").path("findings")) {
      for (JsonNode citation : finding.path("citations")) {
        assertThat(citation.has("citation")).isFalse();
        assertThat(citation.has("sectionKey")).isFalse();
      }
    }
  }
}
