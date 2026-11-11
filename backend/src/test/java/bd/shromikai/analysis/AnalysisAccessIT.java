package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * T-07: another subject, a guest, or a user of the same organisation gets 404 on the result, the
 * events, the trace, the obligations and DELETE of an analysis; the owner still has it afterwards.
 */
@Requirement("FR-54")
@Requirement("NFR-12")
class AnalysisAccessIT extends ApiAnalysisIT {

  private String user(UUID id, UUID org) {
    return jwt.issueUser(id, Role.WORKER, org, false).value();
  }

  private void assertAllNotFound(String stranger, String id) {
    for (String path :
        List.of(
            "/api/v1/analyses/" + id,
            "/api/v1/analyses/" + id + "/trace",
            "/api/v1/analyses/" + id + "/obligations")) {
      ResponseEntity<JsonNode> response = get(stranger, path);
      assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().path("code").asText()).as(path).isEqualTo("not-found");
    }
    HttpHeaders sse = bearer(stranger);
    sse.set(HttpHeaders.ACCEPT, "text/event-stream");
    ResponseEntity<String> events =
        http.exchange(
            "/api/v1/analyses/" + id + "/events",
            HttpMethod.GET,
            new HttpEntity<>(sse),
            String.class);
    assertThat(events.getStatusCode()).as("events").isEqualTo(HttpStatus.NOT_FOUND);
    ResponseEntity<JsonNode> deleted =
        http.exchange(
            "/api/v1/analyses/" + id,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(stranger)),
            JsonNode.class);
    assertThat(deleted.getStatusCode()).as("DELETE").isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anotherUserAGuestAndAColleagueOfTheSameOrganisationCannotReachAnAnalysis() {
    UUID org =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values ('Org', 'HR', 'TEAM') returning id")
            .query(UUID.class)
            .single();
    try {
      String owner = user(UUID.randomUUID(), org);
      String id = postJson(owner).getBody().path("analysisId").asText();

      assertAllNotFound(worker(), id);
      assertAllNotFound(jwt.issueGuest(Hashing.sha256Hex("some-device")).value(), id);
      assertAllNotFound(user(UUID.randomUUID(), org), id);

      assertThat(get(owner, "/api/v1/analyses/" + id).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(get(owner, "/api/v1/analyses/" + id + "/trace").getStatusCode())
          .isEqualTo(HttpStatus.OK);
    } finally {
      jdbc.sql("delete from analysis_run").update();
      jdbc.sql("delete from organisation where id = :id").param("id", org).update();
    }
  }

  @Test
  void anUnknownAnalysisIsNotFoundForEveryone() {
    assertThat(get(worker(), "/api/v1/analyses/" + UUID.randomUUID()).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
