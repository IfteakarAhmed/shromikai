package bd.shromikai.law.internal;

import bd.shromikai.law.IngestOutcome;
import bd.shromikai.law.LawIngestService;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** {@code ./gradlew ingestLaws}: runs one ingestion, logs the summary and exits (FR-15). */
@Component
@ConditionalOnProperty(prefix = "shromikai.ingest", name = "cli", havingValue = "true")
final class IngestCliRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(IngestCliRunner.class);

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
    int code = 1;
    try {
      IngestOutcome outcome =
          service.run(properties.acts() == null ? null : new HashSet<>(properties.acts()));
      LOG.info(
          "ingestion {} release={} report={}",
          outcome.status(),
          outcome.corpusRelease(),
          outcome.reportDirectory());
      outcome
          .acts()
          .forEach(
              a ->
                  LOG.info(
                      "act {} source={} sections={} new={} unchanged={} withdrawn={} failure={}",
                      a.code(),
                      a.sourceKind(),
                      a.sections(),
                      a.newVersions(),
                      a.unchanged(),
                      a.withdrawn(),
                      a.failure()));
      outcome.failures().forEach(f -> LOG.warn("failure: {}", f));
      code = outcome.succeeded() ? 0 : 1;
    } catch (RuntimeException e) {
      LOG.error("ingestion could not run: {}", e.getMessage());
    }
    int exit = code;
    System.exit(SpringApplication.exit(context, () -> exit));
  }
}
