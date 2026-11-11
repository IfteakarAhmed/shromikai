package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.privacy.LocalEntities;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.testsupport.AnalysisHarness;
import bd.shromikai.testsupport.AnthropicStub;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-33: a document that is not an employment contract fails with 422 {@code
 * not-an-employment-contract}.
 */
@IntegrationTest
@ActiveProfiles({"test", "dev"})
@Requirement("FR-33")
class DocumentGateIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired FakeChatPort fake;
  @Autowired AnalysisService analyses;

  @BeforeEach
  void setUp() {
    fake.reset();
    fake.respondWith("pii-extract", r -> AnalysisHarness.piiAnswer(LocalEntities.NONE));
    fake.respondWith(
        "clause-classify", r -> AnthropicStub.answer(r.system() + "\n" + r.user().reveal()));
    fake.respondWith(
        "clause-segment", r -> AnthropicStub.answer(r.system() + "\n" + r.user().reveal()));
  }

  private String rental() throws Exception {
    return new String(
        getClass().getResourceAsStream("/fixtures/documents/rental-agreement.txt").readAllBytes(),
        StandardCharsets.UTF_8);
  }

  @Test
  void shouldReturn422ForARentalAgreementFixture() throws Exception {
    HttpHeaders headers = bearer(jwt.issueUser(UUID.randomUUID(), Role.ADMIN, null, false).value());
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/dev/analyses",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("text", rental()), headers),
            JsonNode.class);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(response.getBody().get("code").asText()).isEqualTo("not-an-employment-contract");
  }

  @Test
  void shouldFailTheJobOnTheClassifyStageAndKeepNoClauses() throws Exception {
    AnalysisJob job = analyses.analyseText(rental());

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.FAILED);
    assertThat(job.stage()).isEqualTo(Stage.CLASSIFY);
    assertThat(job.clauses()).isEmpty();
    assertThat(job.trace().getLast().outcome()).isEqualTo(TraceEvent.FAILED);
  }
}
