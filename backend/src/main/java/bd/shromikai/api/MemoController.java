package bd.shromikai.api;

import bd.shromikai.memo.MemoRenderService;
import bd.shromikai.verify.SignedResultCodec;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-21 {@code POST /api/v1/memos/render} (FR-55): renders the memo of a signed result envelope as
 * PDF or HTML. The envelope is the result the client received, minus its {@code envelope} property,
 * plus the four envelope fields; a changed byte is {@code 400 signature-invalid}, an old envelope
 * {@code 410 envelope-expired}. The request body holds re-identified text and is never logged or
 * stored (R-02).
 */
@RestController
@RequestMapping("/api/v1/memos")
public class MemoController {

  private final MemoRenderService memos;

  public MemoController(MemoRenderService memos) {
    this.memos = memos;
  }

  @Schema(name = "SignedEnvelope")
  public record Envelope(
      @NotNull JsonNode payload,
      @NotNull String keyId,
      @NotNull String algorithm,
      @NotNull Instant expiresAt,
      @NotNull String signature) {}

  @Schema(name = "MemoRenderRequest")
  public record RenderRequest(
      @NotNull @Valid Envelope envelope, @NotNull MemoRenderService.Format format) {}

  @PostMapping(
      path = "/render",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = {MediaType.APPLICATION_PDF_VALUE, MediaType.TEXT_HTML_VALUE})
  public ResponseEntity<byte[]> render(@Valid @RequestBody RenderRequest request) {
    Envelope e = request.envelope();
    MemoRenderService.Rendered rendered =
        memos.render(
            new SignedResultCodec.Envelope(
                e.payload(), e.keyId(), e.algorithm(), e.expiresAt(), e.signature()),
            request.format());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, rendered.contentType())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(rendered.body());
  }
}
