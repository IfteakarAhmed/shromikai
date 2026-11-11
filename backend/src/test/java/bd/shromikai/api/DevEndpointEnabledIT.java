package bd.shromikai.api;

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
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * FR-24, FR-33, AGENTS acceptance: with the {@code dev} profile the debug endpoint shows the
 * redacted clauses and their types for each synthetic contract; a renamed executable, a rental
 * agreement and an oversized upload are refused with problem documents.
 */
@IntegrationTest
@ActiveProfiles({"test", "dev"})
@Requirement("FR-33")
@Requirement("FR-24")
class DevEndpointEnabledIT {

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired FakeChatPort fake;
  @Autowired JdbcClient jdbc;

  private String admin;

  @BeforeEach
  void setUp() {
    admin = jwt.issueUser(UUID.randomUUID(), Role.ADMIN, null, false).value();
    fake.reset();
    LocalEntities all = LocalEntities.NONE;
    for (String id : SyntheticContracts.IDS) {
      all = all.merge(AnalysisHarness.entitiesOf(id));
    }
    LocalEntities union = all;
    fake.respondWith("pii-extract", request -> AnalysisHarness.piiAnswer(union));
    fake.respondWith(
        "clause-classify",
        request -> AnthropicStub.answer(request.system() + "\n" + request.user().reveal()));
    fake.respondWith(
        "clause-segment",
        request -> AnthropicStub.answer(request.system() + "\n" + request.user().reveal()));
  }

  static List<String> contracts() {
    return SyntheticContracts.IDS;
  }

  private ResponseEntity<JsonNode> postText(String text) {
    HttpHeaders headers = bearer(admin);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        "/api/v1/dev/analyses",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("text", text), headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> postFile(String filename, byte[] bytes) {
    HttpHeaders headers = bearer(admin);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "file",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    return http.exchange(
        "/api/v1/dev/analyses", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldShowRedactedClausesAndTheirTypesForEachSyntheticContract(String id) {
    long blocksBefore =
        jdbc.sql("select count(*) from audit_event where action = 'PRIVACY_BLOCK'")
            .query(Long.class)
            .single();

    ResponseEntity<JsonNode> started = postText(SyntheticContracts.text(id));

    assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String analysisId = started.getBody().get("analysisId").asText();
    ResponseEntity<JsonNode> view =
        http.exchange(
            "/api/v1/dev/analyses/{id}/redacted",
            HttpMethod.GET,
            new HttpEntity<>(bearer(admin)),
            JsonNode.class,
            analysisId);
    assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode clauses = view.getBody().get("clauses");
    assertThat(clauses).hasSize(SyntheticContracts.expectedClauseCount(id));
    List<String> texts = new ArrayList<>();
    for (JsonNode clause : clauses) {
      assertThat(clause.get("type").asText()).isNotBlank();
      assertThat(clause.get("confidence").asDouble()).isBetween(0.0, 1.0);
      texts.add(clause.get("redactedText").asText());
    }
    String all = String.join("\n", texts);
    assertThat(all).contains("[PERSON_1]");
    for (SyntheticContracts.Entity entity : SyntheticContracts.entities(id)) {
      assertThat(all).as(entity.type()).doesNotContain(entity.value());
    }
    assertThat(view.getBody().get("trace").findValuesAsText("stage"))
        .contains("INTAKE", "REDACT", "CLASSIFY");
    SyntheticContracts.values(id, "NID", "PHONE", "EMAIL")
        .forEach(v -> assertThat(view.getBody().toString()).doesNotContain(v));
    long blocksAfter =
        jdbc.sql("select count(*) from audit_event where action = 'PRIVACY_BLOCK'")
            .query(Long.class)
            .single();
    assertThat(blocksAfter).as("zero blocked calls in the normal flow").isEqualTo(blocksBefore);
  }

  @Test
  void shouldRejectARenamedExecutableWithAProblemJsonDocument() {
    byte[] exe = new byte[2048];
    exe[0] = 'M';
    exe[1] = 'Z';

    ResponseEntity<JsonNode> response = postFile("invoice.pdf", exe);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(response.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(response.getBody().get("code").asText()).isEqualTo("unsupported-media-type");
  }

  @Test
  void shouldRejectAnUploadOverTenMegabytesWith413() {
    byte[] big = new byte[11 * 1024 * 1024];
    System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);

    ResponseEntity<JsonNode> response = postFile("big.pdf", big);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(response.getBody().get("code").asText()).isEqualTo("payload-too-large");
  }

  @Test
  void shouldReturn404ForAnUnknownAnalysis() {
    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/dev/analyses/{id}/redacted",
            HttpMethod.GET,
            new HttpEntity<>(bearer(admin)),
            JsonNode.class,
            UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
