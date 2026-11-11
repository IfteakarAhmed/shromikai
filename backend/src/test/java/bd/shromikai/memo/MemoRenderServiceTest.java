package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.memo.MemoRenderService.Format;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.SignedResultCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-55: the envelope is opened before anything is rendered; HTML and PDF formats. */
@Tag("unit")
@Requirement("FR-55")
class MemoRenderServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
  private final SignedResultCodec codec =
      new SignedResultCodec(
          "unit-test-envelope-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
          "k1",
          Duration.ofHours(24),
          clock);
  private final AtomicReference<String> rendered = new AtomicReference<>();
  private final MemoRenderService service =
      new MemoRenderService(
          codec,
          new MemoHtmlRenderer(),
          html -> {
            rendered.set(html);
            return "%PDF-fake".getBytes(StandardCharsets.UTF_8);
          });

  private SignedResultCodec.Envelope envelope() throws Exception {
    ObjectNode payload =
        (ObjectNode)
            MAPPER.readTree(
                "{\"memo\":{\"language\":\"en\",\"title\":\"Memo\",\"sections\":[],\"footer\":{}},"
                    + "\"findings\":[],\"redlines\":[],\"obligations\":[]}");
    return codec.sign(payload);
  }

  @Test
  void htmlIsReturnedAsUtf8Html() throws Exception {
    var out = service.render(envelope(), Format.HTML);

    assertThat(out.contentType()).isEqualTo("text/html; charset=utf-8");
    assertThat(new String(out.body(), StandardCharsets.UTF_8)).contains("<title>Memo</title>");
    assertThat(rendered.get()).isNull();
  }

  @Test
  void pdfGoesThroughTheRendererWithTheMemoHtml() throws Exception {
    var out = service.render(envelope(), Format.PDF);

    assertThat(out.contentType()).isEqualTo("application/pdf");
    assertThat(new String(out.body(), StandardCharsets.UTF_8)).startsWith("%PDF");
    assertThat(rendered.get()).contains("<title>Memo</title>");
  }

  @Test
  void aTamperedEnvelopeIsRejectedBeforeAnythingIsRendered() throws Exception {
    var good = envelope();
    ObjectNode changed = good.payload().deepCopy();
    ((ObjectNode) changed.path("memo")).put("title", "Memo!");

    assertThatThrownBy(
            () ->
                service.render(
                    new SignedResultCodec.Envelope(
                        changed,
                        good.keyId(),
                        good.algorithm(),
                        good.expiresAt(),
                        good.signature()),
                    Format.PDF))
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.SIGNATURE_INVALID));
    assertThat(rendered.get()).isNull();
  }
}
