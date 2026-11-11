package bd.shromikai.memo;

import bd.shromikai.verify.SignedResultCodec;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * Renders a memo from a signed result envelope (E-21, FR-55): the envelope is opened first (a
 * changed byte is {@code signature-invalid}, an old one {@code envelope-expired}), and only then is
 * the memo built from its payload. Nothing about the envelope is stored.
 */
@Service
public class MemoRenderService {

  /** The output format of {@code POST /api/v1/memos/render}. */
  public enum Format {
    PDF("application/pdf"),
    HTML("text/html; charset=utf-8");

    private final String contentType;

    Format(String contentType) {
      this.contentType = contentType;
    }

    public String contentType() {
      return contentType;
    }
  }

  /** A rendered memo and its media type. */
  public record Rendered(byte[] body, String contentType) {}

  private final SignedResultCodec codec;
  private final MemoHtmlRenderer html;
  private final PdfRenderer pdf;

  public MemoRenderService(SignedResultCodec codec, MemoHtmlRenderer html, PdfRenderer pdf) {
    this.codec = codec;
    this.html = html;
    this.pdf = pdf;
  }

  public Rendered render(SignedResultCodec.Envelope envelope, Format format) {
    JsonNode payload = codec.open(envelope);
    String document = html.render(payload);
    byte[] body =
        format == Format.PDF ? pdf.pdf(document) : document.getBytes(StandardCharsets.UTF_8);
    return new Rendered(body, format.contentType());
  }
}
