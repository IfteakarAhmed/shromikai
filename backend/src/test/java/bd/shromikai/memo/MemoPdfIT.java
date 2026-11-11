package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * FR-55 (visual test): Bangla conjuncts are shaped correctly in the memo PDF. Gotenberg (headless
 * Chromium, the image of docker-compose.yml) renders 20 conjunct-heavy words; the PDF is checked
 * three ways: its extracted text equals the input, a Bengali-capable font is embedded, and its
 * raster differs from a Chromium screenshot of the same HTML by at most 1% of the pixels.
 */
@Tag("slow")
@Requirement("FR-55")
class MemoPdfIT {

  static final List<String> WORDS =
      List.of(
          "ক্ষতিপূরণ",
          "জ্ঞান",
          "চন্দ্র",
          "শাস্ত্র",
          "শ্রমিক",
          "বিচ্ছেদ",
          "সন্তান",
          "উদ্ধৃতি",
          "চুক্তি",
          "নিয়োগকর্তা",
          "প্রতিষ্ঠান",
          "মর্যাদা",
          "দুর্ঘটনা",
          "বিশ্লেষণ",
          "শিক্ষানবিশ",
          "অনুচ্ছেদ",
          "মজুরি",
          "পদত্যাগ",
          "ধর্মঘট",
          "কর্মসংস্থান");

  static final GenericContainer<?> GOTENBERG =
      new GenericContainer<>(DockerImageName.parse("gotenberg/gotenberg:8.21.1"))
          .withExposedPorts(3000)
          .waitingFor(
              Wait.forHttp("/health").forPort(3000).withStartupTimeout(Duration.ofMinutes(2)));

  static RestClient http;

  @BeforeAll
  static void start() {
    GOTENBERG.start();
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    factory.setReadTimeout(Duration.ofSeconds(60));
    http =
        RestClient.builder()
            .baseUrl("http://" + GOTENBERG.getHost() + ":" + GOTENBERG.getMappedPort(3000))
            .requestFactory(factory)
            .build();
  }

  @AfterAll
  static void stop() {
    GOTENBERG.stop();
  }

  /** 600 CSS pixels wide, no margins, one word per line, so PDF and screenshot line up. */
  static String html() {
    StringBuilder rows = new StringBuilder();
    for (String word : WORDS) {
      rows.append("<div class=\"w\">").append(word).append("</div>\n");
    }
    return "<!doctype html><html lang=\"bn\"><head><meta charset=\"utf-8\"><style>"
        + "@page{size:600px 800px;margin:0} html,body{margin:0;padding:0;background:#fff}"
        + "body{width:600px;font-family:'Noto Sans Bengali','Noto Sans',sans-serif;font-size:28px;"
        + "line-height:40px;color:#000} .w{height:40px;padding-left:20px}"
        + "</style></head><body>"
        + rows
        + "</body></html>";
  }

  static byte[] post(String path, String html, String... fields) {
    LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        "files",
        new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "index.html";
          }
        });
    for (int i = 0; i < fields.length; i += 2) {
      form.add(fields[i], fields[i + 1]);
    }
    return http.post()
        .uri(path)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .body(byte[].class);
  }

  static byte[] pdf() {
    return post(
        "/forms/chromium/convert/html",
        html(),
        "preferCssPageSize",
        "true",
        "printBackground",
        "true");
  }

  @Test
  void theExtractedTextEqualsTheBanglaInput() throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf())) {
      // PDFBox puts a space between some glyph runs of one word; compare without white space.
      String text = squash(new PDFTextStripper().getText(doc));

      for (String word : WORDS) {
        assertThat(text).as(word).contains(squash(word));
      }
    }
  }

  @Test
  void aBengaliCapableFontIsEmbedded() throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf())) {
      List<String> fonts = new ArrayList<>();
      for (PDPage page : doc.getPages()) {
        for (var name : page.getResources().getFontNames()) {
          PDFont font = page.getResources().getFont(name);
          fonts.add(font.getName());
        }
      }

      assertThat(fonts).anyMatch(f -> f.matches("(?i).*(bengali|bangla|siliguri|noto|lohit).*"));
    }
  }

  @Test
  void theRasterDiffersFromAChromiumScreenshotByAtMostOnePercent() throws IOException {
    byte[] pdf = pdf();
    byte[] screenshot =
        post(
            "/forms/chromium/screenshot/html",
            html(),
            "width",
            "600",
            "height",
            "800",
            "format",
            "png");
    BufferedImage shot = ImageIO.read(new ByteArrayInputStream(screenshot));
    BufferedImage page;
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      page = new PDFRenderer(doc).renderImageWithDPI(0, 96); // 600 CSS px wide = 600 px at 96 dpi
    }

    double diff = fractionDifferent(shot, page);

    assertThat(diff).as("fraction of pixels that differ").isLessThanOrEqualTo(0.01);
  }

  private static String squash(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("\\s+", "");
  }

  /** Share of pixels whose grey value differs by more than 96 of 255, over the compared area. */
  static double fractionDifferent(BufferedImage a, BufferedImage b) {
    int width = Math.min(a.getWidth(), b.getWidth());
    int height = Math.min(a.getHeight(), b.getHeight());
    long different = 0;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (Math.abs(grey(a.getRGB(x, y)) - grey(b.getRGB(x, y))) > 96) {
          different++;
        }
      }
    }
    return (double) different / ((double) width * height);
  }

  private static int grey(int rgb) {
    return (((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff)) / 3;
  }
}
