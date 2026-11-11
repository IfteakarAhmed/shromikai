package bd.shromikai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import bd.shromikai.ingest.internal.OcrEngine;
import bd.shromikai.ingest.internal.TesseractCliEngine;
import bd.shromikai.testsupport.BlockExternalHostsExtension;
import bd.shromikai.testsupport.Requirement;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-27, M-24, NFR-06: a synthetic Bangla and English contract image is read locally with Tesseract
 * {@code ben+eng} (the {@code shromikai-tesseract} image of {@code docker/tesseract}). A heavily
 * blurred page fails with {@code unreadable-document} naming the page. OCR makes no network
 * request.
 */
@Tag("slow")
@Requirement("FR-27")
class OcrIT {

  static final String IMAGE = "shromikai-tesseract";
  static final Path FIXTURES = Path.of("src/test/resources/fixtures/ocr");

  private static final List<String> COMMAND =
      List.of("docker", "run", "-i", "--rm", IMAGE, "stdin", "stdout", "-l", "ben+eng", "tsv");

  @BeforeAll
  static void tesseractImage() throws Exception {
    assumeTrue(run("docker", "version") == 0, "Docker is needed for OcrIT");
    if (run("docker", "image", "inspect", IMAGE) != 0) {
      assertThat(run("docker", "build", "-q", "-t", IMAGE, "../docker/tesseract"))
          .as("docker build of the Tesseract image")
          .isZero();
    }
  }

  private static int run(String... command) throws Exception {
    Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
    p.getInputStream().readAllBytes();
    return p.waitFor(300, TimeUnit.SECONDS) ? p.exitValue() : -1;
  }

  private static OcrEngine engine() {
    return new TesseractCliEngine(COMMAND, Duration.ofSeconds(90));
  }

  private static BufferedImage image(String name) throws IOException {
    return ImageIO.read(FIXTURES.resolve(name).toFile());
  }

  private static byte[] bytes(String name) throws IOException {
    return Files.readAllBytes(FIXTURES.resolve(name));
  }

  @Test
  void shouldReadASyntheticContractImageAboveTheThresholdWithCharacterAccuracyOf090()
      throws Exception {
    OcrEngine.Page page = engine().read(image("ocr-bn-en-01.png"));

    String truth =
        Files.readString(FIXTURES.resolve("ocr-bn-en-01.txt"), StandardCharsets.UTF_8)
            .replaceAll("\\s+", " ")
            .strip();
    String actual = page.text().replaceAll("\\s+", " ").strip();
    double accuracy = similarity(truth, actual);
    System.out.printf(
        "OcrIT: mean confidence %.1f, character accuracy %.3f%n", page.confidence(), accuracy);
    assertThat(page.confidence()).isGreaterThan(60.0);
    assertThat(accuracy).isGreaterThanOrEqualTo(0.90);
  }

  @Test
  void shouldRecordOcrConfidenceThroughIntake() throws Exception {
    var intake =
        new IntakeService(
            new IntakeProperties(
                10L << 20,
                15,
                36_000_000L,
                6000,
                Duration.ofSeconds(10),
                Duration.ofSeconds(90),
                60.0,
                4,
                200,
                4,
                COMMAND),
            engine());

    IntakeResult result = intake.fromFiles(List.of(bytes("ocr-bn-en-01.png")));

    assertThat(result.kind()).isEqualTo(InputKind.IMAGE);
    assertThat(result.pages().get(0).method()).isEqualTo(PageQuality.OCR);
    assertThat(result.pages().get(0).ocrConfidence()).isGreaterThan(60.0);
    assertThat(result.paragraphs()).isNotEmpty();
    assertThat(result.language()).isEqualTo("mixed");
    assertThat(BlockExternalHostsExtension.blockedHosts())
        .as("OCR made no network request")
        .isEmpty();
  }

  @Test
  void shouldFailWithUnreadableDocumentNamingTheBlurredPage() throws Exception {
    var intake =
        new IntakeService(
            new IntakeProperties(
                10L << 20,
                15,
                36_000_000L,
                6000,
                Duration.ofSeconds(10),
                Duration.ofSeconds(90),
                60.0,
                4,
                200,
                4,
                COMMAND),
            engine());

    assertThatThrownBy(
            () -> intake.fromFiles(List.of(bytes("ocr-bn-en-01.png"), bytes("ocr-blurred-01.png"))))
        .isInstanceOfSatisfying(
            UnreadableDocumentException.class, e -> assertThat(e.pages()).containsExactly(2));
  }

  private static double similarity(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) d[i][0] = i;
    for (int j = 0; j <= b.length(); j++) d[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
      }
    }
    return 1.0 - (double) d[a.length()][b.length()] / Math.max(a.length(), b.length());
  }
}
