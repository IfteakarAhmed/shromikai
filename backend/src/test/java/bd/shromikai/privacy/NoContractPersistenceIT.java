package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisService;
import bd.shromikai.analysis.JobStore;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.text.BanglaNormalizer;
import bd.shromikai.testsupport.AnalysisHarness;
import bd.shromikai.testsupport.AnthropicStub;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * FR-32, NFR-05, M-17 = 0: after analyses of the canary fixtures (and a blocked egress), every text
 * and jsonb column of every table, the captured log output and the temp directory hold no canary
 * marker, no PII value and no window of 8 consecutive tokens of any canary document.
 */
@IntegrationTest
@Requirement("FR-32")
@Requirement("NFR-05")
class NoContractPersistenceIT {

  private static final int WINDOW = 8;

  @Autowired AnalysisService analyses;
  @Autowired bd.shromikai.analysis.AnalysisPipeline pipeline;
  @Autowired JobStore jobs;
  @Autowired JdbcClient jdbc;
  @Autowired FakeChatPort fake;
  @Autowired EgressGuard guard;

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private Set<Path> tempBefore;

  @BeforeEach
  void setUp() throws IOException {
    logs.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    tempBefore = listTemp();
    fake.reset();
    LocalEntities all = LocalEntities.NONE;
    for (String id : SyntheticContracts.IDS) {
      all = all.merge(AnalysisHarness.entitiesOf(id));
    }
    LocalEntities union = all;
    fake.respondWith("pii-extract", request -> AnalysisHarness.piiAnswer(union));
    fake.respondWith(
        "clause-classify", r -> AnthropicStub.answer(r.system() + "\n" + r.user().reveal()));
    fake.respondWith(
        "clause-segment", r -> AnthropicStub.answer(r.system() + "\n" + r.user().reveal()));
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    logs.stop();
  }

  @Test
  void shouldFindNoCanaryInAnyColumnLogOrTempFileAfterAnalyses() throws IOException {
    List<String> documents = new ArrayList<>();
    List<String> secrets = new ArrayList<>();
    for (String id : SyntheticContracts.IDS) {
      String text = SyntheticContracts.text(id);
      documents.add(text);
      secrets.add(SyntheticContracts.marker(id));
      SyntheticContracts.entities(id).forEach(e -> secrets.add(e.value()));
      AnalysisJob job = analyses.analyseText(text);
      assertThat(job.status()).isEqualTo(AnalysisJob.Status.CLASSIFIED);
    }
    // A blocked egress writes an audit row: it must hold a reason code, never the value.
    ChatRequest leaking =
        new ChatRequest(
            "clause-classify",
            1,
            ModelRole.FAST,
            "claude-haiku-5-5",
            "s",
            SensitiveText.of(
                "worker NID No: 1234567890123 " + SyntheticContracts.marker("syn-en-01")),
            new ObjectMapper().createObjectNode(),
            10,
            Duration.ofSeconds(1),
            null,
            null);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> guard.check(leaking))
        .isInstanceOf(EgressBlockedException.class);
    assertThat(
            jdbc.sql("select count(*) from audit_event where action = 'PRIVACY_BLOCK'")
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(1);

    Windows windows = new Windows(documents);

    List<String> offenders = new ArrayList<>();
    scanDatabase(secrets, windows, offenders);
    scanLogs(secrets, windows, offenders);
    scanTemp(secrets, windows, offenders);
    assertThat(offenders).as("places where contract text or PII was found").isEmpty();
  }

  /**
   * P6 tables (architecture §14): after full pipeline runs where the scripted risk agent copies a
   * clause into its explanation and its claim, a schema-invalid answer ends a job and an egress
   * block stops another, no table (named: review_item.risk_summary, review_item.claimed_claim,
   * review_item.facts, analysis_trace.detail) holds a canary or an 8-token window. The copied claim
   * is stored in the facts-only form with claim_reduced = true.
   */
  @Test
  void shouldFindNoCanaryInTheAnalysisTablesAfterFullPipelineRunsThatTryToLeak()
      throws IOException {
    bd.shromikai.testsupport.FictionalCorpus.load(jdbc);
    try {
      bd.shromikai.testsupport.P6Stubs.install(fake);
      // The risk agent copies the clause text into its explanation and into the claim of a citation
      // whose quote it invents, so the finding ends UNCERTAIN and a review item is opened.
      fake.respondWith(
          "risk-analysis",
          request -> {
            String user = request.user().reveal();
            com.fasterxml.jackson.databind.JsonNode clauses =
                bd.shromikai.testsupport.P6Stubs.clausesOf(request);
            com.fasterxml.jackson.databind.JsonNode candidates =
                bd.shromikai.testsupport.P6Stubs.candidatesOf(request);
            com.fasterxml.jackson.databind.node.ObjectNode out =
                new ObjectMapper().createObjectNode();
            out.put("status", "OK").put("reason", "NONE");
            com.fasterxml.jackson.databind.node.ArrayNode findings = out.putArray("findings");
            if (!clauses.isEmpty() && !candidates.isEmpty()) {
              String copied = clauses.get(0).path("text").asText();
              com.fasterxml.jackson.databind.node.ObjectNode f = findings.addObject();
              f.put("title", "Copied clause");
              f.put("kind", "CONFLICT").put("severity", "HIGH").put("non_obvious", false);
              f.put("clause_type", "OTHER");
              f.putArray("clause_ids").add(clauses.get(0).path("id").asText());
              f.put("explanation", copied);
              f.put("why_it_matters", copied);
              f.putArray("citations")
                  .addObject()
                  .put("section_key", candidates.get(0).path("sectionKey").asText())
                  .put("quote", "an invented quote that the section does not contain at all")
                  .put("claim", copied);
              f.put("confidence", 0.5);
            }
            return out;
          });
      List<String> documents = new ArrayList<>();
      List<String> secrets = new ArrayList<>();
      for (String id : List.of("syn-en-01", "syn-en-02", "syn-bn-01")) {
        String text = SyntheticContracts.text(id);
        documents.add(text);
        secrets.add(SyntheticContracts.marker(id));
        SyntheticContracts.entities(id).forEach(e -> secrets.add(e.value()));
        AnalysisJob job = analyses.analyseText(text);
        pipeline.run(
            job,
            bd.shromikai.analysis.AnalysisRequest.of(bd.shromikai.analysis.Side.WORKER),
            bd.shromikai.analysis.AnalysisSubject.dev());
        assertThat(job.status()).isEqualTo(AnalysisJob.Status.ANALYSED);
      }
      assertThat(
              jdbc.sql("select count(*) from review_item where claim_reduced")
                  .query(Long.class)
                  .single())
          .as("the copied claim was reduced to the facts-only form")
          .isPositive();
      // A schema-invalid answer ends a job without leaving anything behind.
      fake.respondWith(
          "risk-analysis", request -> new ObjectMapper().createObjectNode().put("not", "valid"));
      AnalysisJob broken = analyses.analyseText(SyntheticContracts.text("syn-en-01"));
      pipeline.run(
          broken,
          bd.shromikai.analysis.AnalysisRequest.of(bd.shromikai.analysis.Side.WORKER),
          bd.shromikai.analysis.AnalysisSubject.dev());
      assertThat(broken.status()).isEqualTo(AnalysisJob.Status.FAILED);

      List<String> offenders = new ArrayList<>();
      scanDatabase(secrets, new Windows(documents), offenders);
      assertThat(offenders).as("places where contract text or PII was found").isEmpty();
    } finally {
      jdbc.sql("delete from analysis_run").update();
      bd.shromikai.testsupport.FictionalCorpus.clean(jdbc);
    }
  }

  @Test
  void shouldDropEverythingWhenAJobIsDeleted() {
    AnalysisJob job = analyses.analyseText(SyntheticContracts.text("syn-bn-01"));
    assertThat(job.clauses()).isNotEmpty();

    boolean existed = jobs.delete(job.id());

    assertThat(existed).isTrue();
    assertThat(job.clauses()).isEmpty();
    assertThat(job.trace()).isEmpty();
    assertThat(jobs.find(job.id())).isEmpty();
  }

  @Test
  void shouldNoticeALeakWhenOneExists() {
    String doc = SyntheticContracts.text("syn-en-01");
    Windows windows = new Windows(List.of(doc));
    String firstWindow =
        String.join(" ", java.util.Arrays.copyOfRange(doc.trim().split("\\s+"), 3, 11));

    assertThat(windows.hit("log line: " + firstWindow + " end", List.of())).isNotNull();
    assertThat(windows.hit("value ZQXJ-CANARY-3A4B5C6D", List.of("ZQXJ-CANARY-3A4B5C6D")))
        .isNotNull();
    assertThat(windows.hit("{\"reason\":\"pii_detected\"}", List.of("ZQXJ-CANARY-3A4B5C6D")))
        .isNull();
  }

  // ---------------------------------------------------------------------------------------

  private void scanDatabase(List<String> secrets, Windows windows, List<String> offenders) {
    List<Map<String, Object>> columns =
        jdbc.sql(
                """
                select c.table_name, c.column_name
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'public' and t.table_type = 'BASE TABLE'
                  and c.data_type in ('text', 'character varying', 'character', 'jsonb', 'json', 'ARRAY')
                """)
            .query()
            .listOfRows();
    assertThat(columns).as("text and jsonb columns scanned").isNotEmpty();
    assertThat(columns.stream().map(r -> r.get("table_name") + "." + r.get("column_name")).toList())
        .contains(
            "audit_event.detail",
            "analysis_trace.detail",
            "review_item.risk_summary",
            "review_item.claimed_claim",
            "review_item.facts");
    for (Map<String, Object> column : columns) {
      String table = (String) column.get("table_name");
      String name = (String) column.get("column_name");
      List<String> values =
          jdbc.sql(
                  "select cast(\""
                      + name
                      + "\" as text) from \""
                      + table
                      + "\" where \""
                      + name
                      + "\" is not null")
              .query(String.class)
              .list();
      for (String value : values) {
        String hit = windows.hit(value, secrets);
        if (hit != null) {
          offenders.add("db " + table + "." + name + ": " + hit);
        }
      }
    }
  }

  private void scanLogs(List<String> secrets, Windows windows, List<String> offenders) {
    for (ILoggingEvent event : new ArrayList<>(logs.list)) {
      StringBuilder sb = new StringBuilder(event.getFormattedMessage());
      event
          .getKeyValuePairs()
          .forEach(kv -> sb.append(' ').append(kv.key).append('=').append(kv.value));
      event.getMDCPropertyMap().forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
      if (event.getThrowableProxy() != null) {
        sb.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
      }
      String hit = windows.hit(sb.toString(), secrets);
      if (hit != null) {
        offenders.add("log " + event.getLoggerName() + ": " + hit);
      }
    }
  }

  private void scanTemp(List<String> secrets, Windows windows, List<String> offenders)
      throws IOException {
    for (Path file : listTemp()) {
      if (tempBefore.contains(file) || !Files.isRegularFile(file) || Files.size(file) > 8_000_000) {
        continue;
      }
      String text;
      try {
        text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      } catch (IOException e) {
        continue;
      }
      String hit = windows.hit(text, secrets);
      if (hit != null) {
        offenders.add("temp " + file.getFileName() + ": " + hit);
      }
    }
  }

  private static Set<Path> listTemp() throws IOException {
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
    Set<Path> out = new HashSet<>();
    try (Stream<Path> walk = Files.walk(tmp, 4)) {
      walk.forEach(out::add);
    } catch (IOException | java.io.UncheckedIOException e) {
      // unreadable sub-directories of the shared temp dir are not ours
    }
    return out;
  }

  /** All 8-token windows of the canary documents, and the search over a stored value. */
  private static final class Windows {
    private final Set<String> windows = new HashSet<>();

    Windows(List<String> documents) {
      for (String document : documents) {
        windows.addAll(windowsOf(document));
      }
      assertThat(windows).isNotEmpty();
    }

    /** A short description of what was found (never the value itself), or {@code null}. */
    String hit(String value, List<String> secrets) {
      String normal = Normalizer.normalize(value, Normalizer.Form.NFC);
      String latin = BanglaNormalizer.toLatinDigits(normal);
      for (String secret : secrets) {
        if (normal.contains(secret) || latin.contains(BanglaNormalizer.toLatinDigits(secret))) {
          return "a canary marker or PII value";
        }
      }
      for (String window : windowsOf(value)) {
        if (windows.contains(window)) {
          return "8 consecutive tokens of a canary document";
        }
      }
      return null;
    }

    private static Set<String> windowsOf(String text) {
      String[] tokens = Normalizer.normalize(text, Normalizer.Form.NFC).trim().split("\\s+");
      Set<String> out = new HashSet<>();
      for (int i = 0; i + WINDOW <= tokens.length; i++) {
        out.add(String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + WINDOW)));
      }
      return out;
    }
  }
}
