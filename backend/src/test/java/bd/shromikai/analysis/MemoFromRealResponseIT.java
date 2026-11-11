package bd.shromikai.analysis;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.memo.PdfRenderer;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * FR-55: a memo renders as HTML and as PDF from an envelope built, as api.md §3 prescribes, from a
 * real {@code GET /api/v1/analyses/{id}} response; one changed byte returns 400 {@code
 * signature-invalid}. The PDF engine is replaced by a stub here; {@code MemoPdfIT} runs the real
 * one.
 */
@Requirement("FR-55")
@Import(MemoFromRealResponseIT.StubPdf.class)
class MemoFromRealResponseIT extends ApiAnalysisIT {

  static final AtomicReference<String> RENDERED_HTML = new AtomicReference<>();

  @TestConfiguration(proxyBeanMethods = false)
  static class StubPdf {
    @Bean
    @Primary
    PdfRenderer stubPdfRenderer() {
      return html -> {
        RENDERED_HTML.set(html);
        return "%PDF-1.7 stub".getBytes(StandardCharsets.UTF_8);
      };
    }
  }

  /** The envelope of a result exactly as a client builds it. */
  private static ObjectNode envelopeOf(JsonNode result) {
    ObjectNode payload = result.deepCopy();
    payload.remove("envelope");
    ObjectNode envelope = payload.objectNode();
    envelope.set("payload", payload);
    envelope.put("keyId", result.path("envelope").path("keyId").asText());
    envelope.put("algorithm", result.path("envelope").path("algorithm").asText());
    envelope.put("expiresAt", result.path("envelope").path("expiresAt").asText());
    envelope.put("signature", result.path("envelope").path("signature").asText());
    return envelope;
  }

  private ResponseEntity<byte[]> render(String token, ObjectNode envelope, String format) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ObjectNode body = envelope.objectNode();
    body.set("envelope", envelope);
    body.put("format", format);
    return http.exchange(
        "/api/v1/memos/render", HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
  }

  @Test
  void aMemoRendersAsHtmlAndPdfFromTheEnvelopeOfARealGetResponse() {
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();
    JsonNode result = get(token, "/api/v1/analyses/" + id).getBody();

    ResponseEntity<byte[]> html = render(token, envelopeOf(result), "HTML");
    ResponseEntity<byte[]> pdf = render(token, envelopeOf(result), "PDF");

    assertThat(html.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(html.getHeaders().getContentType().toString()).startsWith("text/html");
    String page = new String(html.getBody(), StandardCharsets.UTF_8);
    assertThat(page).contains(result.path("memo").path("title").asText());
    assertThat(page).contains("Stand-in summary.");
    assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(pdf.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    assertThat(new String(pdf.getBody(), StandardCharsets.UTF_8)).startsWith("%PDF");
    assertThat(RENDERED_HTML.get()).contains("Stand-in summary.");
  }

  @Test
  void oneChangedByteIsA400SignatureInvalid() {
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();
    ObjectNode envelope = envelopeOf(get(token, "/api/v1/analyses/" + id).getBody());
    ObjectNode payload = (ObjectNode) envelope.path("payload");
    ObjectNode first = (ObjectNode) payload.path("findings").get(0);
    first.put("title", first.path("title").asText() + "!");

    ResponseEntity<byte[]> response = render(token, envelope, "PDF");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
        .contains("signature-invalid");
  }

  @Test
  void aWorkerWhoNeverAnalysedCanStillRenderAnEnvelopeTheyHold() {
    // the envelope is the proof, not the account: it was signed for 24 hours and carries no subject
    String token = worker();
    String id = postJson(token).getBody().path("analysisId").asText();
    ObjectNode envelope = envelopeOf(get(token, "/api/v1/analyses/" + id).getBody());

    assertThat(render(worker(), envelope, "HTML").getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
