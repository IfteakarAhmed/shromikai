package bd.shromikai.law;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.IdentityFixtures;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FR-15, FR-13, FR-16, FR-17 end to end against saved pages of the official portal served by
 * WireMock: the first run stores sections, versions, graph, terms, a corpus release and the quality
 * report; a second run changes nothing; a changed page makes a new version; a failed run commits
 * nothing.
 */
@IntegrationTest
@Requirement("FR-15")
class IngestionIdempotencyIT {

  private static final Path TMP = tempDir();
  private static final String FIXTURES = "src/test/resources/fixtures/law/";

  @RegisterExtension
  static WireMockExtension portal =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("shromikai.ingest.pack-file", () -> writePack().toString());
    registry.add("shromikai.ingest.mapping-file", () -> TMP.resolve("none.yaml").toString());
    registry.add("shromikai.ingest.report-dir", () -> TMP.resolve("reports").toString());
    registry.add("shromikai.scraper.raw-dir", () -> TMP.resolve("raw").toString());
    registry.add("shromikai.scraper.contact", () -> "ops@example.org");
    registry.add("shromikai.scraper.user-agent", () -> "ShromikAI-ingest/1.0 (+ops@example.org)");
    registry.add("shromikai.scraper.min-interval", () -> "0s");
  }

  @Autowired LawIngestService service;
  @Autowired JdbcClient jdbc;
  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired DataSource dataSource;

  @BeforeEach
  void servePortal() throws IOException {
    awaitIdle();
    clean();
    portal.resetAll();
    stubPortal(read("labour-act-2006/print.html"), read("labour-act-2006/index.html"));
  }

  @AfterEach
  void cleanAfter() {
    awaitIdle();
    clean();
  }

  void clean() {
    jdbc.sql("delete from law_act where code like 'T-%'").update();
    jdbc.sql("delete from corpus_release").update();
  }

  @Test
  void shouldStoreSectionsVersionsGraphTermsReleaseAndReport_onFirstRun() throws IOException {
    IngestOutcome outcome = service.run(null);

    assertThat(outcome.status()).as(outcome.failures().toString()).isEqualTo("SUCCEEDED");
    assertThat(outcome.corpusRelease()).matches("\\d{4}\\.\\d{2}\\.\\d{2}-1");
    assertThat(
            count(
                "select count(*) from law_section where act_id = (select id from law_act where code = 'T-LAB')"))
        .isEqualTo(9);
    assertThat(
            count(
                "select count(*) from law_section where is_current and act_id = (select id from law_act where code = 'T-LAB')"))
        .isEqualTo(8);
    assertThat(
            count(
                "select count(*) from law_section where act_id = (select id from law_act where code = 'T-CON')"))
        .isEqualTo(6);

    Map<String, Object> s100 =
        jdbc.sql("select * from law_section where section_key = 'T-LAB:100@original'")
            .query()
            .singleRow();
    assertThat(s100.get("is_current")).isEqualTo(true);
    assertThat(s100.get("section_no_original")).isEqualTo("১০০");
    assertThat(s100.get("text_bn").toString()).startsWith("১০০।");
    assertThat(s100.get("text_en")).isNull();
    assertThat(s100.get("content_hash").toString()).matches("[0-9a-f]{64}");
    assertThat(s100.get("parser_version")).isEqualTo("bdlaws-print-1");
    assertThat(s100.get("effective_from").toString()).isEqualTo("2006-10-11");
    assertThat(s100.get("normalised_text").toString()).contains("100.");
    assertThat(s100.get("source_url").toString())
        .startsWith(portal.baseUrl() + "/act-952/section-");
    assertThat(s100.get("chapter").toString()).contains("কমর্ঘন্টা");

    assertThat(key("T-LAB:175@amend-2026")).as("substituted by the 2026 amending act").isTrue();
    assertThat(key("T-LAB:3A@amend-2013")).isTrue();
    Map<String, Object> omitted =
        jdbc.sql("select * from law_section where section_key = 'T-LAB:184@amend-2026'")
            .query()
            .singleRow();
    assertThat(omitted.get("is_current")).isEqualTo(false);
    assertThat(omitted.get("amendment_notes").toString()).contains("OMITTED");

    assertThat(count("select count(*) from section_edge where kind = 'REFERS_TO'")).isPositive();
    assertThat(edge("T-LAB:175@amend-2026", "T-LAB:1@amend-2026", "REFERS_TO"))
        .as("ধারা ১ এর উপ-ধারা (৪)")
        .isTrue();
    assertThat(edge("T-LAB:175@amend-2026", "T-LAB:2@amend-2026", "USES_DEFINITION")).isTrue();
    assertThat(
            count(
                "select count(*) from law_term where kind = 'DEFINED_TERM' and code like 'T-LAB:%'"))
        .isGreaterThan(50);

    JsonNode report =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(TMP.resolve("reports/corpus-quality.json").toFile());
    assertThat(report.get("status").asText()).isEqualTo("PASSED");
    assertThat(report.get("corpusRelease").asText()).isEqualTo(outcome.corpusRelease());
    assertThat(report.get("samples")).hasSize(5);
    assertThat(Files.readString(TMP.resolve("reports/corpus-quality.md")))
        .contains("T-LAB")
        .contains("T-CON")
        .contains("PASSED");

    ResponseEntity<JsonNode> meta = http.getForEntity("/api/v1/meta", JsonNode.class);
    assertThat(meta.getBody().at("/corpus/release").asText()).isEqualTo(outcome.corpusRelease());
    assertThat(meta.getBody().at("/corpus/currentSections").asLong()).isEqualTo(12);
    assertThat(meta.getBody().at("/corpus/acts").asLong()).isEqualTo(2);
    assertThat(meta.getBody().at("/corpus/lastRefreshAt").isNull()).isFalse();
  }

  @Test
  void shouldCreateNoDuplicates_andTheSameReleaseHash_whenRunTwice() {
    IngestOutcome first = service.run(null);
    long sections = count("select count(*) from law_section");
    long edges = count("select count(*) from section_edge");
    long terms = count("select count(*) from law_term");

    IngestOutcome second = service.run(null);

    assertThat(second.succeeded()).isTrue();
    assertThat(count("select count(*) from law_section")).isEqualTo(sections);
    assertThat(count("select count(*) from section_edge")).isEqualTo(edges);
    assertThat(count("select count(*) from law_term")).isEqualTo(terms);
    assertThat(second.acts()).allSatisfy(a -> assertThat(a.newVersions()).isZero());
    List<String> shas =
        jdbc.sql("select sha256 from corpus_release order by id").query(String.class).list();
    assertThat(shas).hasSize(2).containsOnly(shas.get(0));
    assertThat(first.corpusRelease()).isNotEqualTo(second.corpusRelease());
  }

  @Test
  void shouldCreateNewVersionAndNewRelease_whenAPageChanges() throws IOException {
    service.run(null);
    String firstSha =
        jdbc.sql("select sha256 from corpus_release order by id desc limit 1")
            .query(String.class)
            .single();
    long sections = count("select count(*) from law_section");
    String changed =
        read("labour-act-2006/print.html").replace("দৈনিক আট ঘণ্টার", "দৈনিক নয় ঘণ্টার");
    assertThat(changed).isNotEqualTo(read("labour-act-2006/print.html"));
    stubPortal(changed, read("labour-act-2006/index.html"));

    IngestOutcome outcome = service.run(null);

    assertThat(outcome.succeeded()).isTrue();
    assertThat(count("select count(*) from law_section")).isEqualTo(sections + 1);
    assertThat(
            jdbc.sql("select is_current from law_section where section_key = 'T-LAB:100@original'")
                .query(Boolean.class)
                .single())
        .isFalse();
    assertThat(
            jdbc.sql(
                    "select is_current from law_section where section_key = 'T-LAB:100@original-r2'")
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(edge("T-LAB:100@original", "T-LAB:100@original-r2", "AMENDED_BY")).isTrue();
    assertThat(
            jdbc.sql("select text_bn from law_section where section_key = 'T-LAB:100@original'")
                .query(String.class)
                .single())
        .contains("আট ঘণ্টার"); // the old text is kept, not rewritten
    assertThat(
            jdbc.sql("select sha256 from corpus_release order by id desc limit 1")
                .query(String.class)
                .single())
        .isNotEqualTo(firstSha);
  }

  @Test
  void shouldMakeASectionNonCurrent_whenTheSourceNoLongerListsIt() throws IOException {
    service.run(null);
    Document print = Jsoup.parse(read("labour-act-2006/print.html"));
    print.select("div.lineremoves").stream()
        .filter(e -> e.text().contains("১০০।"))
        .forEach(e -> e.remove());
    Document index = Jsoup.parse(read("labour-act-2006/index.html"));
    index.select("p.act-section-name").stream()
        .filter(e -> e.text().contains("১০০।"))
        .forEach(e -> e.remove());
    stubPortal(print.outerHtml(), index.outerHtml());

    IngestOutcome outcome = service.run(Set.of("T-LAB"));

    assertThat(outcome.succeeded()).isTrue();
    assertThat(
            jdbc.sql("select is_current from law_section where section_key = 'T-LAB:100@original'")
                .query(Boolean.class)
                .single())
        .isFalse();
    assertThat(outcome.acts().get(0).withdrawn()).isEqualTo(1);
  }

  @Test
  void shouldCommitNothingAndCreateNoRelease_whenARequiredActCannotBeAcquired() throws IOException {
    service.run(null);
    long sections = count("select count(*) from law_section");
    long releases = count("select count(*) from corpus_release");
    portal.stubFor(get(urlEqualTo("/act-952.html")).willReturn(aResponse().withStatus(404)));

    IngestOutcome outcome = service.run(null);

    assertThat(outcome.status()).isEqualTo("FAILED");
    assertThat(outcome.corpusRelease()).isNull();
    assertThat(outcome.failures()).anyMatch(f -> f.startsWith("ACT_FAILED") && f.contains("T-LAB"));
    assertThat(count("select count(*) from law_section")).isEqualTo(sections);
    assertThat(count("select count(*) from corpus_release")).isEqualTo(releases);
    assertThat(Files.readString(TMP.resolve("reports/corpus-quality.md"))).contains("FAILED");
  }

  @Test
  void shouldRefuseASecondRun_whileOneIsRunning() {
    portal.stubFor(
        get(urlEqualTo("/act-print-952.html"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(read("labour-act-2006/print.html").getBytes(StandardCharsets.UTF_8))
                    .withFixedDelay(1500)));

    service.startAsync(Set.of("T-LAB"));

    assertThatThrownBy(() -> service.startAsync(null))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.INGEST_RUNNING));
    awaitIdle();
    assertThat(count("select count(*) from corpus_release")).isEqualTo(1); // the first run finished
  }

  @Test
  void shouldAcceptIngestRequestFromAdminOnly() {
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    json.setBearerAuth(IdentityFixtures.userToken(jwt, Role.ADMIN));
    HttpHeaders worker = new HttpHeaders();
    worker.setContentType(MediaType.APPLICATION_JSON);
    worker.setBearerAuth(IdentityFixtures.userToken(jwt, Role.WORKER));

    ResponseEntity<JsonNode> denied =
        http.exchange(
            "/api/v1/admin/laws/ingest",
            HttpMethod.POST,
            new HttpEntity<>("{}", worker),
            JsonNode.class);
    ResponseEntity<JsonNode> bad =
        http.exchange(
            "/api/v1/admin/laws/ingest",
            HttpMethod.POST,
            new HttpEntity<>("{\"acts\":[\"not valid!\"]}", json),
            JsonNode.class);
    ResponseEntity<JsonNode> accepted =
        http.exchange(
            "/api/v1/admin/laws/ingest",
            HttpMethod.POST,
            new HttpEntity<>("{\"acts\":[\"T-LAB\"]}", json),
            JsonNode.class);

    assertThat(denied.getStatusCode().value()).isEqualTo(403);
    assertThat(bad.getStatusCode().value()).isEqualTo(400);
    assertThat(accepted.getStatusCode().value()).isEqualTo(202);
    assertThat(UUID.fromString(accepted.getBody().get("ingestRunId").asText())).isNotNull();
    awaitIdle();
    assertThat(count("select count(*) from corpus_release")).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------------------------

  /** Waits until no ingestion holds the advisory lock (a run started by an earlier test). */
  private void awaitIdle() {
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              try (Connection c = dataSource.getConnection();
                  Statement st = c.createStatement();
                  ResultSet rs =
                      st.executeQuery(
                          "select pg_try_advisory_lock(" + LawIngestService.LOCK_KEY + ")")) {
                rs.next();
                boolean free = rs.getBoolean(1);
                if (free) {
                  try (Statement unlock = c.createStatement()) {
                    unlock.execute("select pg_advisory_unlock(" + LawIngestService.LOCK_KEY + ")");
                  }
                }
                return free;
              }
            });
  }

  private long count(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  private boolean key(String sectionKey) {
    return count("select count(*) from law_section where section_key = '" + sectionKey + "'") == 1;
  }

  private boolean edge(String from, String to, String kind) {
    return jdbc.sql(
                """
            select count(*) from section_edge e
              join law_section f on f.id = e.from_section_id join law_section t on t.id = e.to_section_id
             where f.section_key = :from and t.section_key = :to and e.kind = :kind
            """)
            .param("from", from)
            .param("to", to)
            .param("kind", kind)
            .query(Long.class)
            .single()
        == 1;
  }

  private void stubPortal(String labourPrint, String labourIndex) throws IOException {
    portal.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    stub("/act-952.html", labourIndex);
    stub("/act-print-952.html", labourPrint);
    stub("/act-26.html", read("contract-act-1872/index.html"));
    stub("/act-print-26.html", read("contract-act-1872/print.html"));
  }

  private static void stub(String path, String body) {
    portal.stubFor(
        get(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html;charset=UTF-8")
                    .withBody(body.getBytes(StandardCharsets.UTF_8))));
  }

  private static String read(String name) {
    try {
      return Files.readString(Path.of(FIXTURES, name));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("shromikai-ingest-it");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path writePack() {
    String yaml =
        """
        code: TT
        sources:
          bdlaws:
            base_url: %s
        acts:
          - code: T-LAB
            jurisdiction: TT
            portal_act_id: 952
            authoritative_lang: bn
            title_en: "Labour fixture act (test)"
            definitions_section: "2"
          - code: T-CON
            jurisdiction: TT
            portal_act_id: 26
            authoritative_lang: en
            title_en: "Contract fixture act (test)"
            definitions_section: "2"
        """
            .formatted(portal.baseUrl());
    try {
      Path file = TMP.resolve("packs/tt/tt.yaml");
      Files.createDirectories(file.getParent());
      Files.writeString(file, yaml);
      return file;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
