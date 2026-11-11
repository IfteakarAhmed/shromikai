package bd.shromikai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.ingest.internal.OcrEngine;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-24, FR-25, FR-27, NFR-20: PDF intake with an in-memory OCR double. */
@Tag("it")
@Requirement("FR-24")
@Requirement("FR-25")
@Requirement("FR-27")
class PdfTextExtractorIT {

  private final List<String> ocrCalls = Collections.synchronizedList(new ArrayList<>());

  private IntakeService service(OcrEngine ocr) {
    return new IntakeService(IntakeProperties.defaults(), ocr);
  }

  private OcrEngine ocr(String text, double confidence) {
    return image -> {
      ocrCalls.add("page");
      return new OcrEngine.Page(text, confidence);
    };
  }

  private static List<String> page(String header, String... body) {
    List<String> lines = new ArrayList<>();
    lines.add(header);
    lines.add("");
    lines.addAll(List.of(body));
    lines.add("Page footer");
    return lines;
  }

  @Test
  void shouldMatchTheGoldenTextOfATextLayerPdfWithSimilarityOfAtLeast098() {
    byte[] pdf =
        PdfFixtures.pdf(
            List.of(
                List.of(
                    "1. The Worker is appointed as a sewing operator.",
                    "2. The monthly wage is BDT 25,000 paid on the seventh working day.")));

    IntakeResult result = service(ocr("", 0)).fromFiles(List.of(pdf));

    String actual = joined(result);
    String golden =
        "1. The Worker is appointed as a sewing operator. "
            + "2. The monthly wage is BDT 25,000 paid on the seventh working day.";
    assertThat(similarity(golden, actual)).isGreaterThanOrEqualTo(0.98);
    assertThat(result.kind()).isEqualTo(InputKind.PDF_TEXT);
    assertThat(result.paragraphs()).hasSize(2);
    assertThat(result.paragraphs().get(0).id()).isEqualTo("p1");
    assertThat(result.language()).isEqualTo("en");
    assertThat(ocrCalls).isEmpty();
  }

  @Test
  void shouldYieldARepeatedHeaderOnceAcrossThreePages() {
    byte[] pdf =
        PdfFixtures.pdf(
            List.of(
                page(
                    "EXAMPLE LTD CONTRACT", "1. First clause is about the appointment of workers."),
                page("EXAMPLE LTD CONTRACT", "2. Second clause is about the monthly wage paid."),
                page("EXAMPLE LTD CONTRACT", "3. Third clause is about notice of termination.")));

    IntakeResult result = service(ocr("", 0)).fromFiles(List.of(pdf));

    String text = joined(result);
    assertThat(text.split("EXAMPLE LTD CONTRACT", -1)).hasSize(2);
    assertThat(text).contains("First clause", "Second clause", "Third clause");
  }

  @Test
  void shouldSendPagesWithoutATextLayerToOcrAndRecordTheirConfidence() {
    byte[] pdf =
        PdfFixtures.pdf(
            List.of(
                List.of(
                    "1. The Worker is appointed as a sewing operator in the factory of the Employer."),
                List.of(),
                List.of()));

    IntakeResult result =
        service(ocr("2. Scanned clause about wages and working hours of the worker.", 91.5))
            .fromFiles(List.of(pdf));

    assertThat(ocrCalls).hasSize(2);
    assertThat(result.kind()).isEqualTo(InputKind.PDF_SCAN);
    assertThat(result.pages())
        .extracting(PageQuality::method)
        .containsExactly(PageQuality.TEXT_LAYER, PageQuality.OCR, PageQuality.OCR);
    assertThat(result.pages().get(1).ocrConfidence()).isEqualTo(91.5);
    assertThat(result.paragraphs().stream().filter(p -> p.page() == 2)).isNotEmpty();
  }

  @Test
  void shouldFlagParagraphsFromLowConfidenceOcrPagesButStillAcceptThem() {
    byte[] pdf = PdfFixtures.pdf(List.of(List.of()));

    IntakeResult result =
        service(ocr("1. Clause about the appointment of the worker in the factory.", 70.0))
            .fromFiles(List.of(pdf));

    assertThat(result.paragraphs()).allSatisfy(p -> assertThat(p.lowOcrConfidence()).isTrue());
  }

  @Test
  void shouldFailWithUnreadableDocumentListingThePageBelowConfidence60() {
    byte[] pdf =
        PdfFixtures.pdf(
            List.of(
                List.of("1. A readable first page about the worker and the employer agreement."),
                List.of()));

    assertThatThrownBy(() -> service(ocr("smudge", 31.0)).fromFiles(List.of(pdf)))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.type()).isEqualTo(ProblemType.UNREADABLE_DOCUMENT);
              assertThat(e.pages()).containsExactly(2);
            });
  }

  @Test
  void shouldRejectAPdfWithMoreThan15PagesWithTooManyPages() {
    byte[] pdf =
        PdfFixtures.pdf(
            java.util.Collections.nCopies(
                16, List.of("Some page text here, long enough to count as text layer content.")));

    assertThatThrownBy(() -> service(ocr("", 0)).fromFiles(List.of(pdf)))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.TOO_MANY_PAGES));
  }

  @Test
  void shouldRejectUploadsOver10MbWithPayloadTooLarge() {
    byte[] big = new byte[11 * 1024 * 1024];
    big[0] = '%';
    big[1] = 'P';
    big[2] = 'D';
    big[3] = 'F';
    big[4] = '-';

    assertThatThrownBy(() -> service(ocr("", 0)).fromFiles(List.of(big)))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.PAYLOAD_TOO_LARGE));
  }

  @Test
  void shouldRefuseADecompressionBombPngBeforeDecodingIt() {
    // 20000 x 20000 pixels of one colour compresses to a few hundred kilobytes.
    byte[] bomb = PdfFixtures.png(20_000, 20_000);
    assertThat(bomb.length).isLessThan(5 * 1024 * 1024);

    assertThatThrownBy(() -> service(ocr("", 0)).fromFiles(List.of(bomb)))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.PAYLOAD_TOO_LARGE));
    assertThat(ocrCalls).isEmpty();
  }

  @Test
  void shouldOcrImagesAndRejectMixedUploads() {
    byte[] png = PdfFixtures.pngWithText("Employment agreement");

    IntakeResult result =
        service(ocr("1. Clause one is about the appointment of the worker as an operator.", 88))
            .fromFiles(List.of(png, png));

    assertThat(result.kind()).isEqualTo(InputKind.IMAGE);
    assertThat(result.pageCount()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                service(ocr("", 0)).fromFiles(List.of(png, PdfFixtures.pdf(List.of(List.of("x"))))))
        .isInstanceOf(ProblemException.class);
  }

  @Test
  void shouldAcceptPastedTextBetween200And60000CharactersOnly() {
    IntakeService service = service(ocr("", 0));
    String ok = ("1. The Worker shall work eight hours a day. ").repeat(6);

    IntakeResult result = service.fromText(ok);

    assertThat(result.kind()).isEqualTo(InputKind.TEXT);
    assertThatThrownBy(() -> service.fromText("too short")).isInstanceOf(ProblemException.class);
    assertThatThrownBy(() -> service.fromText("x".repeat(60_001)))
        .isInstanceOf(ProblemException.class);
  }

  private static String joined(IntakeResult result) {
    StringBuilder sb = new StringBuilder();
    result.paragraphs().forEach(p -> sb.append(p.text().reveal()).append(' '));
    return sb.toString().strip();
  }

  /** 1 - normalised edit distance. */
  private static double similarity(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      d[0][j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
      }
    }
    return 1.0 - (double) d[a.length()][b.length()] / Math.max(a.length(), b.length());
  }
}
