package bd.shromikai.law.internal;

import bd.shromikai.law.IngestOutcome;
import bd.shromikai.law.LawIngestService;
import bd.shromikai.shared.logging.LogKeys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * {@code ./gradlew ingestLaws}: runs one ingestion, logs the outcome and exits (FR-15).
 *
 * <p>Project loggers print only the message template and allowlisted keys (NFR-18, R-02), so a
 * cause travels as {@code errorCode}, {@code reasonCode} (exception class) and {@code httpStatus};
 * free text such as URLs stays in the quality report.
 */
@Component
@ConditionalOnProperty(prefix = "shromikai.ingest", name = "cli", havingValue = "true")
final class IngestCliRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(IngestCliRunner.class);
  private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_-]+");
  private static final int MAX_CODE_TOKENS = 3;

  private final LawIngestService service;
  private final IngestProperties properties;
  private final ApplicationContext context;

  IngestCliRunner(
      LawIngestService service, IngestProperties properties, ApplicationContext context) {
    this.service = service;
    this.properties = properties;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    int exit = execute();
    System.exit(SpringApplication.exit(context, () -> exit));
  }

  /** Runs the ingestion, logs the outcome and returns the process exit code. */
  int execute() {
    try {
      IngestOutcome outcome =
          service.run(properties.acts() == null ? null : new HashSet<>(properties.acts()));
      for (IngestOutcome.ActSummary act : outcome.acts()) {
        var event = LOG.atInfo().addKeyValue(LogKeys.STATUS, act.sourceKind());
        if (act.failure() != null) {
          event =
              LOG.atWarn()
                  .addKeyValue(LogKeys.STATUS, act.sourceKind())
                  .addKeyValue(LogKeys.ERROR_CODE, failureCode(act.failure()));
        }
        event.log("act processed");
      }
      for (String failure : outcome.failures()) {
        LOG.atWarn().addKeyValue(LogKeys.ERROR_CODE, failureCode(failure)).log("ingestion failure");
      }
      LOG.atInfo()
          .addKeyValue(LogKeys.STATUS, outcome.status())
          .log("ingestion finished, details in the quality report under eval/reports");
      return outcome.succeeded() ? 0 : 1;
    } catch (RuntimeException e) {
      var event =
          LOG.atError()
              .addKeyValue(LogKeys.ERROR_CODE, errorCode(e))
              .addKeyValue(LogKeys.REASON_CODE, rootCauseClass(e));
      if (e instanceof FetchException fetch && fetch.status() > 0) {
        event = event.addKeyValue(LogKeys.HTTP_STATUS, fetch.status());
      }
      event.log("ingestion could not run");
      return 1;
    }
  }

  static String errorCode(Throwable e) {
    return e instanceof FetchException fetch ? fetch.reason().name() : "INGEST_ERROR";
  }

  /** Simple class name of the innermost cause: the exception message is never logged. */
  static String rootCauseClass(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getClass().getSimpleName();
  }

  /**
   * The leading code words of a failure line ("ACT_FAILED: BLA-2006: HTML_UNUSABLE: ..." becomes
   * {@code ACT_FAILED/BLA-2006/HTML_UNUSABLE}); stops at the first token that is not a plain code,
   * so URLs and other free text never reach the log.
   */
  static String failureCode(String failure) {
    List<String> tokens = new ArrayList<>();
    for (String token : failure.split(": ", MAX_CODE_TOKENS + 1)) {
      if (tokens.size() == MAX_CODE_TOKENS || !SAFE_TOKEN.matcher(token).matches()) {
        break;
      }
      tokens.add(token);
    }
    return tokens.isEmpty() ? "UNKNOWN" : String.join("/", tokens);
  }
}
