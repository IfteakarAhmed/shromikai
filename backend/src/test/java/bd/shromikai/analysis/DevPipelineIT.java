package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.testsupport.FictionalCorpus;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.P6Stubs;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base of the integration tests that run the whole P6 pipeline through the {@code dev} endpoint
 * with scripted stand-ins for the models and a fictional act in the database (pack xx, R-10).
 */
@IntegrationTest
@ActiveProfiles({"test", "dev"})
abstract class DevPipelineIT {

  @Autowired protected TestRestTemplate http;
  @Autowired protected JwtService jwt;
  @Autowired protected FakeChatPort fake;
  @Autowired protected JdbcClient jdbc;

  protected String admin;

  @BeforeEach
  void installStandIns() {
    admin = jwt.issueUser(UUID.randomUUID(), Role.ADMIN, null, false).value();
    FictionalCorpus.load(jdbc);
    fake.reset();
    P6Stubs.install(fake);
  }

  @AfterEach
  void cleanDatabase() {
    jdbc.sql("delete from analysis_run").update(); // cascades to trace and review items
    FictionalCorpus.clean(jdbc);
  }

  /** Runs a synthetic contract through the full pipeline; returns the analysis ID. */
  protected String run(String contractId, String side) {
    HttpHeaders headers = bearer(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> started =
        http.exchange(
            "/api/v1/dev/analyses?full=true&side=" + side,
            HttpMethod.POST,
            new HttpEntity<>(Map.of("text", SyntheticContracts.text(contractId)), headers),
            JsonNode.class);
    assertThat(started.getStatusCode())
        .as(String.valueOf(started.getBody()))
        .isEqualTo(HttpStatus.CREATED);
    assertThat(started.getBody().get("status").asText()).isEqualTo("ANALYSED");
    return started.getBody().get("analysisId").asText();
  }

  protected JsonNode findings(String analysisId) {
    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/dev/analyses/{id}/findings",
            HttpMethod.GET,
            new HttpEntity<>(bearer(admin)),
            JsonNode.class,
            analysisId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  protected JsonNode redacted(String analysisId) {
    return http.exchange(
            "/api/v1/dev/analyses/{id}/redacted",
            HttpMethod.GET,
            new HttpEntity<>(bearer(admin)),
            JsonNode.class,
            analysisId)
        .getBody();
  }
}
