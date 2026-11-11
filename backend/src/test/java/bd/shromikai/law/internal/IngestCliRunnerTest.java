package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bd.shromikai.law.IngestOutcome;
import bd.shromikai.law.LawIngestService;
import bd.shromikai.shared.logging.AllowlistJsonFormatter;
import bd.shromikai.testsupport.Requirement;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * FR-15: when {@code ingestLaws} fails, the log line says why. Project loggers print only the
 * message template and allowlisted keys (NFR-18), so the cause must travel as keys: {@code
 * errorCode}, {@code reasonCode} (exception class) and {@code httpStatus}. Placeholders such as
 * {@code {}} must never appear unresolved.
 */
@Tag("unit")
@Requirement("FR-15")
class IngestCliRunnerTest {

  private final ObjectMapper json = new ObjectMapper();
  private final LawIngestService service = mock(LawIngestService.class);
  private final IngestProperties properties =
      new IngestProperties(Path.of("p"), Path.of("m"), Path.of("r"), true, null, null);
  private final IngestCliRunner runner =
      new IngestCliRunner(service, properties, mock(ApplicationContext.class));
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(IngestCliRunner.class);

  @BeforeEach
  void capture() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void release() {
    logger.detachAppender(appender);
  }

  private List<JsonNode> lines() throws Exception {
    AllowlistJsonFormatter formatter = new AllowlistJsonFormatter();
    List<JsonNode> out = new java.util.ArrayList<>();
    for (ILoggingEvent event : appender.list) {
      out.add(json.readTree(formatter.format(event)));
    }
    return out;
  }

  @Test
  void shouldLogExceptionClassAndCode_whenTheRunThrows() throws Exception {
    when(service.run(any())).thenThrow(new IllegalStateException("SCRAPER_CONTACT is not set"));

    int code = runner.execute();

    assertThat(code).isEqualTo(1);
    JsonNode line = lines().get(lines().size() - 1);
    assertThat(line.get("level").asText()).isEqualTo("ERROR");
    assertThat(line.get("message").asText()).doesNotContain("{}");
    assertThat(line.get("reasonCode").asText()).isEqualTo("IllegalStateException");
    assertThat(line.get("errorCode").asText()).isEqualTo("INGEST_ERROR");
  }

  @Test
  void shouldLogFetchReason_andRootCauseClass_whenTheServerIsUnreachable() throws Exception {
    when(service.run(any()))
        .thenThrow(
            new FetchException(
                FetchException.Reason.UNREACHABLE,
                "GET http://x failed",
                0,
                new SocketTimeoutException("read timed out")));

    runner.execute();

    JsonNode line = lines().get(lines().size() - 1);
    assertThat(line.get("errorCode").asText()).isEqualTo("UNREACHABLE");
    assertThat(line.get("reasonCode").asText()).isEqualTo("SocketTimeoutException");
    assertThat(line.has("httpStatus")).isFalse();
  }

  @Test
  void shouldLogHttpStatus_whenTheServerRefuses() throws Exception {
    when(service.run(any()))
        .thenThrow(
            new FetchException(
                FetchException.Reason.HTTP_ERROR, "GET http://x answered 403", 403, null));

    runner.execute();

    JsonNode line = lines().get(lines().size() - 1);
    assertThat(line.get("errorCode").asText()).isEqualTo("HTTP_ERROR");
    assertThat(line.get("httpStatus").asText()).isEqualTo("403");
  }

  @Test
  void shouldLogActAndReasonCode_forEveryFailureOfAFailedRun() throws Exception {
    when(service.run(any()))
        .thenReturn(
            new IngestOutcome(
                "FAILED",
                null,
                List.of(
                    new IngestOutcome.ActSummary(
                        "BLA-2006", "NONE", 0, 0, 0, 0, "HTML_UNUSABLE: GET http://x failed")),
                List.of(
                    "ACT_FAILED: BLA-2006: HTML_UNUSABLE: GET http://x/act-952.html failed",
                    "MISSING_SOURCE_URL: 1 section(s) of BLA-2006 have no source URL"),
                "/tmp/reports"));

    int code = runner.execute();

    assertThat(code).isEqualTo(1);
    List<JsonNode> all = lines();
    assertThat(all).allSatisfy(l -> assertThat(l.get("message").asText()).doesNotContain("{}"));
    assertThat(all)
        .anyMatch(l -> "ACT_FAILED/BLA-2006/HTML_UNUSABLE".equals(textOrNull(l, "errorCode")));
    assertThat(all).anyMatch(l -> "MISSING_SOURCE_URL".equals(textOrNull(l, "errorCode")));
    assertThat(all)
        .noneMatch(l -> l.toString().contains("http://x")); // free text stays in the report
  }

  @Test
  void shouldLogStatusAndExitZero_whenTheRunSucceeds() throws Exception {
    when(service.run(any()))
        .thenReturn(
            new IngestOutcome(
                "SUCCEEDED",
                "2026.11.11-1",
                List.of(new IngestOutcome.ActSummary("BLA-2006", "HTML", 369, 369, 0, 0, null)),
                List.of(),
                "/tmp/reports"));

    int code = runner.execute();

    assertThat(code).isZero();
    List<JsonNode> all = lines();
    assertThat(all).allSatisfy(l -> assertThat(l.get("message").asText()).doesNotContain("{}"));
    assertThat(all).anyMatch(l -> "SUCCEEDED".equals(textOrNull(l, "status")));
  }

  private static String textOrNull(JsonNode node, String field) {
    return node.has(field) ? node.get(field).asText() : null;
  }
}
