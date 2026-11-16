package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

/**
 * FR-69: {@code sample: true} analyses the bundled synthetic contract without an upload, in the
 * JSON body and in the multipart options, for an analysis and for compare; the result says it is a
 * sample. Content parts or text together with {@code sample} are refused (api.md §4.1).
 */
@Requirement("FR-69")
class SampleAnalysisIT extends ApiAnalysisIT {

  @Autowired SampleContract sample;

  private ResponseEntity<JsonNode> json(String path, String token, Map<String, Object> body) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private static Map<String, Object> sampleBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sample", true);
    body.put("side", "WORKER");
    body.put("outputLanguage", "en");
    return body;
  }

  @Test
  void theBundledSampleIsTheSyntheticContractOfTheEvaluationSet() {
    // The leak-test marker is left out of the sample, so no memo or redline of the demo carries it.
    assertThat(sample.text())
        .doesNotContain("ZQXJ-CANARY")
        .isEqualTo(SyntheticContracts.text("syn-en-02").replaceAll(" ZQXJ-CANARY-[0-9A-F]+", ""));
    assertThat(sample.entities())
        .extracting(SampleContract.Entity::value)
        .contains("Karim Demo Hossain", "Demo Guard Services");
  }

  @Test
  void aJsonSampleRequestIsAcceptedFinishesAndIsMarkedAsASample() throws Exception {
    String token = worker();

    ResponseEntity<JsonNode> accepted = json("/api/v1/analyses", token, sampleBody());

    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode result = awaitResult(token, accepted.getBody().path("analysisId").asText());
    assertThat(result.path("input").path("sample").asBoolean()).isTrue();
    assertThat(new JsonSchemas().validate("schemas/api/analysis-result.v1.schema.json", result))
        .isEmpty();
  }

  @Test
  void aMultipartSampleRequestWithNoContentPartIsAccepted() throws Exception {
    String token = worker();
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpHeaders optionsHeaders = new HttpHeaders();
    optionsHeaders.setContentType(MediaType.APPLICATION_JSON);
    LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add(
        "options",
        new HttpEntity<>(
            "{\"side\":\"WORKER\",\"outputLanguage\":\"en\",\"sample\":true}", optionsHeaders));

    ResponseEntity<JsonNode> accepted =
        http.exchange(
            "/api/v1/analyses", HttpMethod.POST, new HttpEntity<>(parts, headers), JsonNode.class);

    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode result = awaitResult(token, accepted.getBody().path("analysisId").asText());
    assertThat(result.path("input").path("sample").asBoolean()).isTrue();
  }

  @Test
  void aSampleRequestWithTextOrAContentPartIsRefusedAndSoIsNeither() {
    String token = worker();
    Map<String, Object> withText = sampleBody();
    withText.put("text", text());
    Map<String, Object> neither = new LinkedHashMap<>();
    neither.put("side", "WORKER");
    neither.put("outputLanguage", "en");

    assertThat(json("/api/v1/analyses", token, withText).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(json("/api/v1/analyses", token, neither).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpHeaders optionsHeaders = new HttpHeaders();
    optionsHeaders.setContentType(MediaType.APPLICATION_JSON);
    HttpHeaders textHeaders = new HttpHeaders();
    textHeaders.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
    LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add(
        "options",
        new HttpEntity<>(
            "{\"side\":\"WORKER\",\"outputLanguage\":\"en\",\"sample\":true}", optionsHeaders));
    parts.add("text", new HttpEntity<>(text(), textHeaders));
    assertThat(
            http.exchange(
                    "/api/v1/analyses",
                    HttpMethod.POST,
                    new HttpEntity<>(parts, headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void compareRunsOnTheSampleToo() throws Exception {
    String token = worker();

    ResponseEntity<JsonNode> accepted = json("/api/v1/compare", token, sampleBody());

    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String id = accepted.getBody().path("compareId").asText();
    JsonNode result = null;
    for (int i = 0; i < 300 && result == null; i++) {
      ResponseEntity<JsonNode> poll = get(token, "/api/v1/compare/" + id);
      if (poll.getStatusCode() == HttpStatus.OK) {
        result = poll.getBody();
      } else {
        Thread.sleep(50);
      }
    }
    assertThat(result).as("compare finished").isNotNull();
    assertThat(result.at("/shromikai/input/sample").asBoolean()).isTrue();
  }
}
