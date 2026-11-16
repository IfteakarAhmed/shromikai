package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-73: an administrator reads the content-free trace of any analysis; nobody else does through
 * this route, and the trace holds no contract text (R-02).
 */
@Requirement("FR-73")
class AdminTraceIT extends ApiAnalysisIT {

  private String admin() {
    return jwt.issueUser(UUID.randomUUID(), Role.ADMIN, null, false).value();
  }

  @Test
  void anAdminReadsTheTraceOfAnotherUsersAnalysis() throws Exception {
    String owner = worker();
    String id = postJson(owner).getBody().path("analysisId").asText();
    awaitResult(owner, id);

    ResponseEntity<JsonNode> trace = get(admin(), "/api/v1/admin/traces/" + id);

    assertThat(trace.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = trace.getBody();
    assertThat(new JsonSchemas().validate("schemas/api/analysis-trace.v1.schema.json", body))
        .isEmpty();
    assertThat(body.path("analysisId").asText()).isEqualTo(id);
    assertThat(body.path("events").size()).isGreaterThan(3);
    // R-02: nothing of the contract appears in the trace
    assertThat(body.toString()).doesNotContain(text().substring(0, 60));
  }

  @Test
  void aWorkerGets403AndAnUnknownAnalysisIs404() {
    assertThat(get(worker(), "/api/v1/admin/traces/" + UUID.randomUUID()).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get(admin(), "/api/v1/admin/traces/" + UUID.randomUUID()).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
