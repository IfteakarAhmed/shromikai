package bd.shromikai.law.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * FR-12 with WireMock: robots.txt is respected, requests are at least one interval apart, retries
 * back off, pages are decoded by byte-order mark and cached with a checksum, and the PDF is used
 * when the HTML has no section bodies.
 */
@Tag("unit")
@Requirement("FR-12")
class ScraperPolicyTest {

  private static final String AGENT = "ShromikAI-ingest/1.0 (+ops@example.org)";

  @RegisterExtension
  static WireMockExtension portal =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir Path raw;

  private PoliteFetcher fetcher(Duration interval, Duration backoff) {
    return new PoliteFetcher(
        new ScraperSettings(
            AGENT,
            interval,
            Duration.ofSeconds(5),
            2,
            backoff,
            raw,
            ScraperSettings.CachePolicy.REFRESH),
        new RawCache(raw));
  }

  private String url(String path) {
    return portal.baseUrl() + path;
  }

  private void page(String path, String body) {
    portal.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withBody(body)));
  }

  @Test
  void shouldRespectRobotsDisallow_andNeverRequestTheForbiddenPage() {
    portal.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/plain")
                    .withBody("User-agent: *\nDisallow: /private/\nAllow: /private/open")));
    page("/private/secret.html", "x");
    page("/private/open.html", "ok");
    page("/public.html", "ok");
    PoliteFetcher fetcher = fetcher(Duration.ZERO, Duration.ofMillis(10));

    assertThatThrownBy(() -> fetcher.fetch(url("/private/secret.html")))
        .isInstanceOfSatisfying(
            FetchException.class,
            e -> assertThat(e.reason()).isEqualTo(FetchException.Reason.ROBOTS_DISALLOWED));
    assertThat(fetcher.fetch(url("/public.html")).text()).isEqualTo("ok");
    assertThat(fetcher.fetch(url("/private/open.html")).text())
        .isEqualTo("ok"); // longer Allow wins
    portal.verify(0, getRequestedFor(urlEqualTo("/private/secret.html")));
  }

  @Test
  void shouldObeyGroupOfOurProductToken() {
    portal.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/plain")
                    .withBody(
                        "User-agent: *\nDisallow:\n\nUser-agent: ShromikAI-ingest\nDisallow: /")));
    page("/a.html", "ok");

    assertThatThrownBy(() -> fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/a.html")))
        .isInstanceOf(FetchException.class)
        .hasMessageContaining("robots.txt disallows");
  }

  @Test
  void shouldTreatHtmlAnswerAsNoPolicy_likeTheBdlawsPortal() {
    portal.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html;charset=UTF-8")
                    .withBody("<!DOCTYPE html><html><title>404</title>Disallow: /</html>")));
    page("/a.html", "ok");

    assertThat(fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/a.html")).text())
        .isEqualTo("ok");
  }

  @Test
  void shouldTreatMissingRobotsFileAsNoRestriction_butServerErrorAsRefusal() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/a.html", "ok");
    assertThat(fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/a.html")).text())
        .isEqualTo("ok");

    portal.resetAll();
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(503)));
    page("/a.html", "ok");
    assertThatThrownBy(() -> fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/a.html")))
        .isInstanceOfSatisfying(
            FetchException.class,
            e -> assertThat(e.reason()).isEqualTo(FetchException.Reason.ROBOTS_UNAVAILABLE));
  }

  @Test
  void shouldSpaceRequestsByAtLeastOneSecond() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/a.html", "a");
    page("/b.html", "b");
    PoliteFetcher fetcher = fetcher(Duration.ofSeconds(1), Duration.ofMillis(10));

    fetcher.fetch(url("/a.html"));
    fetcher.fetch(url("/b.html"));

    List<LoggedRequest> requests = portal.findAll(getRequestedFor(urlPathEqualTo("/robots.txt")));
    requests.addAll(portal.findAll(getRequestedFor(urlEqualTo("/a.html"))));
    requests.addAll(portal.findAll(getRequestedFor(urlEqualTo("/b.html"))));
    List<Long> times = requests.stream().map(r -> r.getLoggedDate().getTime()).sorted().toList();
    assertThat(times).hasSize(3);
    for (int i = 1; i < times.size(); i++) {
      assertThat(times.get(i) - times.get(i - 1)).isGreaterThanOrEqualTo(990L);
    }
  }

  @Test
  void shouldRetryWithBackoff_onServerErrorsAndTooManyRequests() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    stubSequence("/flaky.html", 503, 429);
    PoliteFetcher fetcher = fetcher(Duration.ZERO, Duration.ofMillis(200));

    assertThat(fetcher.fetch(url("/flaky.html")).text()).isEqualTo("finally");

    List<Long> times =
        portal.findAll(getRequestedFor(urlEqualTo("/flaky.html"))).stream()
            .map(r -> r.getLoggedDate().getTime())
            .sorted()
            .toList();
    assertThat(times).hasSize(3);
    assertThat(times.get(1) - times.get(0)).isGreaterThanOrEqualTo(190L); // backoff 200 ms
    assertThat(times.get(2) - times.get(1)).isGreaterThanOrEqualTo(390L); // doubled: 400 ms
  }

  @Test
  void shouldGiveUpAfterTwoRetries_andNotRetryClientErrors() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    portal.stubFor(get(urlEqualTo("/down.html")).willReturn(aResponse().withStatus(500)));
    portal.stubFor(get(urlEqualTo("/gone.html")).willReturn(aResponse().withStatus(404)));
    PoliteFetcher fetcher = fetcher(Duration.ZERO, Duration.ofMillis(10));

    assertThatThrownBy(() -> fetcher.fetch(url("/down.html")))
        .isInstanceOfSatisfying(FetchException.class, e -> assertThat(e.status()).isEqualTo(500));
    portal.verify(3, getRequestedFor(urlEqualTo("/down.html")));
    assertThatThrownBy(() -> fetcher.fetch(url("/gone.html"))).isInstanceOf(FetchException.class);
    portal.verify(1, getRequestedFor(urlEqualTo("/gone.html")));
  }

  @Test
  void shouldTreatASlowBodyAsATimeout_retryIt_andReportItAsFetchFailure() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    // Headers arrive at once, the body takes longer than the whole-response timeout.
    portal.stubFor(
        get(urlEqualTo("/slow-body.html"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("x".repeat(2000))
                    .withChunkedDribbleDelay(40, 3000)));
    PoliteFetcher fetcher =
        new PoliteFetcher(
            new ScraperSettings(
                AGENT,
                Duration.ZERO,
                Duration.ofMillis(700),
                2,
                Duration.ofMillis(10),
                raw,
                ScraperSettings.CachePolicy.REFRESH),
            new RawCache(raw));

    assertThatThrownBy(() -> fetcher.fetch(url("/slow-body.html")))
        .isInstanceOfSatisfying(
            FetchException.class,
            e -> {
              assertThat(e.reason()).isEqualTo(FetchException.Reason.UNREACHABLE);
              assertThat(IngestCliRunner.rootCauseClass(e)).isEqualTo("SocketTimeoutException");
            });
    portal.verify(3, getRequestedFor(urlEqualTo("/slow-body.html"))); // 1 try + 2 retries
  }

  @Test
  void shouldRecover_whenTheFirstDownloadIsTooSlowButTheRetryIsFast() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    portal.stubFor(
        get(urlEqualTo("/flaky-body.html"))
            .inScenario("slow-then-fast")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("x".repeat(2000))
                    .withChunkedDribbleDelay(40, 3000))
            .willSetStateTo("fast"));
    portal.stubFor(
        get(urlEqualTo("/flaky-body.html"))
            .inScenario("slow-then-fast")
            .whenScenarioStateIs("fast")
            .willReturn(aResponse().withStatus(200).withBody("fast body")));
    PoliteFetcher fetcher =
        new PoliteFetcher(
            new ScraperSettings(
                AGENT,
                Duration.ZERO,
                Duration.ofMillis(700),
                2,
                Duration.ofMillis(10),
                raw,
                ScraperSettings.CachePolicy.REFRESH),
            new RawCache(raw));

    assertThat(fetcher.fetch(url("/flaky-body.html")).text()).isEqualTo("fast body");
    portal.verify(2, getRequestedFor(urlEqualTo("/flaky-body.html")));
  }

  @Test
  void shouldOnlyUseGet_neverHeadOrOtherMethods() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/a.html", "a");
    page("/b.html", "b");
    PoliteFetcher fetcher = fetcher(Duration.ZERO, Duration.ofMillis(10));

    fetcher.fetch(url("/a.html"));
    fetcher.fetch(url("/b.html"));

    List<LoggedRequest> all =
        portal.findAll(
            com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    assertThat(all).hasSize(3); // robots.txt, a, b
    assertThat(all).allSatisfy(r -> assertThat(r.getMethod().getName()).isEqualTo("GET"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shippedConfigurationAllowsASlowResponse_butStaysPolite() throws IOException {
    java.util.Map<String, Object> yaml =
        (java.util.Map<String, Object>)
            new org.yaml.snakeyaml.Yaml()
                .loadAll(Files.newInputStream(Path.of("src/main/resources/application.yml")))
                .iterator()
                .next();
    java.util.Map<String, Object> scraper =
        (java.util.Map<String, Object>)
            ((java.util.Map<String, Object>) yaml.get("shromikai")).get("scraper");

    // The portal builds /act-print-{id}.html slowly (about 20 s measured on 2026-11-11).
    assertThat(Duration.parse("PT" + scraper.get("timeout").toString().toUpperCase()))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(120));
    assertThat(Duration.parse("PT" + scraper.get("min-interval").toString().toUpperCase()))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldSendDescriptiveUserAgentWithContact() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/a.html", "a");

    fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/a.html"));

    portal.verify(getRequestedFor(urlEqualTo("/a.html")).withHeader("User-Agent", equalTo(AGENT)));
    portal.verify(
        getRequestedFor(urlEqualTo("/robots.txt")).withHeader("User-Agent", equalTo(AGENT)));
  }

  @Test
  void shouldDecodeUtf16PagesByByteOrderMark() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    byte[] body = withBom("﻿<html>শ্রমিক ধারা ২৬</html>", StandardCharsets.UTF_16BE);
    portal.stubFor(
        get(urlEqualTo("/utf16.html"))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/html;charset=UTF-8").withBody(body)));

    FetchedPage fetched = fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/utf16.html"));

    assertThat(fetched.text()).isEqualTo("<html>শ্রমিক ধারা ২৬</html>");
    assertThat(fetched.sha256()).isEqualTo(Hashing.sha256Hex(body));
  }

  @Test
  void shouldCacheRawPageWithChecksum() throws IOException {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/act-1.html", "<html>text</html>");
    PoliteFetcher fetcher = fetcher(Duration.ZERO, Duration.ofMillis(10));

    FetchedPage fetched = fetcher.fetch(url("/act-1.html"));

    RawCache cache = new RawCache(raw);
    Path file = cache.pathFor(url("/act-1.html"));
    assertThat(Files.readAllBytes(file))
        .isEqualTo("<html>text</html>".getBytes(StandardCharsets.UTF_8));
    assertThat(Files.readString(RawCache.sidecar(file)).strip()).isEqualTo(fetched.sha256());
    assertThat(cache.load(url("/act-1.html"))).isPresent();

    Files.writeString(file, "tampered"); // checksum no longer matches
    assertThat(cache.load(url("/act-1.html"))).isEmpty();
  }

  @Test
  void shouldReadCacheInsteadOfNetwork_whenPolicyPrefersCache() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/c.html", "cached");
    fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/c.html"));
    PoliteFetcher offline =
        new PoliteFetcher(
            new ScraperSettings(
                AGENT,
                Duration.ZERO,
                Duration.ofSeconds(5),
                2,
                Duration.ofMillis(10),
                raw,
                ScraperSettings.CachePolicy.PREFER_CACHE),
            new RawCache(raw));

    FetchedPage again = offline.fetch(url("/c.html"));

    assertThat(again.fromCache()).isTrue();
    portal.verify(1, getRequestedFor(urlEqualTo("/c.html")));
  }

  @Test
  void shouldUsePdfFallback_whenHtmlHasNoSectionBodies() throws IOException {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/act-9.html", indexWith("1. Short title", "2. Definitions"));
    page("/act-print-9.html", printWithoutBodies(2));
    byte[] pdf =
        pdf(
            "THE FICTIONAL ACT, 2000",
            "1. Short title",
            "This Act may be called the Fictional Act.",
            "2. Definitions",
            "In this Act the word worker means a fictional person.");
    portal.stubFor(
        get(urlEqualTo("/official/act-9.pdf"))
            .willReturn(aResponse().withHeader("Content-Type", "application/pdf").withBody(pdf)));
    ActDefinition def =
        new ActDefinition(
            "XX-2000",
            "XX",
            "9",
            url("/official/act-9.pdf"),
            "en",
            "Fictional Act",
            null,
            null,
            true,
            null);

    AcquiredAct acquired =
        new ActAcquirer(fetcher(Duration.ZERO, Duration.ofMillis(10)), portal.baseUrl())
            .acquire(def);

    assertThat(acquired.ok()).isTrue();
    assertThat(acquired.sourceKind()).isEqualTo(AcquiredAct.SourceKind.PDF);
    assertThat(acquired.parserVersion()).isEqualTo(PdfActParser.PARSER_VERSION);
    assertThat(acquired.act().sections())
        .extracting(ParsedSection::sectionNo)
        .containsExactly("1", "2");
    assertThat(acquired.act().sections().get(1).text()).contains("fictional person");
    portal.verify(1, getRequestedFor(urlEqualTo("/official/act-9.pdf")));
  }

  @Test
  void shouldNotTouchPdf_whenHtmlIsUsable() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/act-9.html", LawFixtures.read("contract-act-1872", "index.html"));
    page("/act-print-9.html", LawFixtures.read("contract-act-1872", "print.html"));
    ActDefinition def =
        new ActDefinition(
            "XX-2000",
            "XX",
            "9",
            url("/official/act-9.pdf"),
            "en",
            "Fictional Act",
            null,
            null,
            true,
            null);

    AcquiredAct acquired =
        new ActAcquirer(fetcher(Duration.ZERO, Duration.ofMillis(10)), portal.baseUrl())
            .acquire(def);

    assertThat(acquired.sourceKind()).isEqualTo(AcquiredAct.SourceKind.HTML);
    assertThat(acquired.sourceSha256()).hasSize(64);
    portal.verify(0, getRequestedFor(urlEqualTo("/official/act-9.pdf")));
  }

  @Test
  void shouldReportFailure_whenNeitherHtmlNorPdfWorks_andNeverInventSections() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    portal.stubFor(get(urlEqualTo("/act-9.html")).willReturn(aResponse().withStatus(404)));
    ActDefinition def =
        new ActDefinition(
            "XX-2000", "XX", "9", null, "en", "Fictional Act", null, null, true, null);

    AcquiredAct acquired =
        new ActAcquirer(fetcher(Duration.ZERO, Duration.ofMillis(10)), portal.baseUrl())
            .acquire(def);

    assertThat(acquired.ok()).isFalse();
    assertThat(acquired.act()).isNull();
    assertThat(acquired.failure()).startsWith("HTML_UNUSABLE");
  }

  @Test
  void shouldRecordActsNotPublishedOnPortal() {
    ActDefinition def =
        new ActDefinition(
            "BLR-2015", "BD", null, null, "bn", "Rules", null, null, false, "not on portal");

    AcquiredAct acquired =
        new ActAcquirer(fetcher(Duration.ZERO, Duration.ofMillis(10)), portal.baseUrl())
            .acquire(def);

    assertThat(acquired.failure()).startsWith("NOT_PUBLISHED_ON_PORTAL");
    portal.verify(0, getRequestedFor(urlEqualTo("/robots.txt")));
  }

  @Test
  void shouldRejectBlankUserAgent() {
    assertThatThrownBy(
            () ->
                new ScraperSettings(
                    " ",
                    Duration.ZERO,
                    Duration.ofSeconds(1),
                    2,
                    Duration.ZERO,
                    raw,
                    ScraperSettings.CachePolicy.REFRESH))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------------------------------------

  private void stubSequence(String path, int first, int second) {
    portal.stubFor(
        get(urlEqualTo(path))
            .inScenario("flaky")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(first))
            .willSetStateTo("second"));
    portal.stubFor(
        get(urlEqualTo(path))
            .inScenario("flaky")
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(second))
            .willSetStateTo("third"));
    portal.stubFor(
        get(urlEqualTo(path))
            .inScenario("flaky")
            .whenScenarioStateIs("third")
            .willReturn(aResponse().withStatus(200).withBody("finally")));
  }

  private static byte[] withBom(String text, java.nio.charset.Charset charset) {
    byte[] encoded = text.getBytes(charset);
    return encoded; // the ﻿ at the start of the text is encoded as the byte-order mark
  }

  private static String indexWith(String... titles) {
    StringBuilder sb =
        new StringBuilder(
            "<html><body><h3>Fictional Act, 2000</h3><div class=\"publish-date\">[ 1st January, 2000 ]</div>");
    int n = 1;
    for (String title : titles) {
      sb.append("<p class=\"act-section-name\"><a href=\"/act-9/section-")
          .append(n++)
          .append(".html\">")
          .append(title)
          .append("</a></p>");
    }
    return sb.append("</body></html>").toString();
  }

  private static String printWithoutBodies(int rows) {
    StringBuilder sb = new StringBuilder("<html><body>");
    for (int i = 0; i < rows; i++) {
      sb.append("<div class=\"row lineremoves\"><div class=\"txt-head\">x</div></div>");
    }
    return sb.append("</body></html>").toString();
  }

  private static byte[] pdf(String... lines) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        content.setLeading(16);
        content.newLineAtOffset(40, 700);
        List<String> all = new ArrayList<>(List.of(lines));
        for (String line : all) {
          content.showText(line);
          content.newLine();
        }
        content.endText();
      }
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void shouldRefuseARedirectToAnotherHost() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    portal.stubFor(
        get(urlEqualTo("/moved.html"))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", "http://evil.example/x.html")));

    assertThatThrownBy(
            () -> fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/moved.html")))
        .isInstanceOfSatisfying(
            FetchException.class,
            e -> assertThat(e.reason()).isEqualTo(FetchException.Reason.REDIRECT_OFF_HOST));
  }

  @Test
  void shouldFollowARedirectOnTheSameHost() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    portal.stubFor(
        get(urlEqualTo("/old.html"))
            .willReturn(aResponse().withStatus(301).withHeader("Location", "/new.html")));
    page("/new.html", "moved here");

    assertThat(fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/old.html")).text())
        .isEqualTo("moved here");
  }

  @Test
  void shouldRefuseABodyOverTheSizeCapInsteadOfTruncatingIt() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/big.html", "x".repeat(2_000));
    var small =
        new PoliteFetcher(
            new ScraperSettings(
                AGENT,
                Duration.ZERO,
                Duration.ofSeconds(5),
                2,
                Duration.ofMillis(10),
                raw,
                ScraperSettings.CachePolicy.REFRESH),
            new RawCache(raw),
            d -> {},
            1_000);

    assertThatThrownBy(() -> small.fetch(url("/big.html")))
        .isInstanceOfSatisfying(
            FetchException.class,
            e -> assertThat(e.reason()).isEqualTo(FetchException.Reason.BODY_TOO_LARGE));
  }

  @Test
  void shouldFallBackToHttpWhenTheHostDoesNotServeHttps() {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    page("/plain.html", "over http");

    assertThat(fetcher(Duration.ZERO, Duration.ofMillis(10)).fetch(url("/plain.html")).text())
        .isEqualTo("over http");
  }
}
