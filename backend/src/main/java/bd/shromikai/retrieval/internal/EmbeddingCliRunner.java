package bd.shromikai.retrieval.internal;

import bd.shromikai.retrieval.BackfillResult;
import bd.shromikai.retrieval.EmbeddingBackfillService;
import bd.shromikai.shared.logging.LogKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** {@code ./gradlew embedLaws}: runs the embedding backfill once, logs the counts and exits. */
@Component
@ConditionalOnProperty(prefix = "shromikai.retrieval", name = "cli", havingValue = "true")
final class EmbeddingCliRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(EmbeddingCliRunner.class);

  private final EmbeddingBackfillService backfill;
  private final ApplicationContext context;

  EmbeddingCliRunner(EmbeddingBackfillService backfill, ApplicationContext context) {
    this.backfill = backfill;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    int exit = execute();
    System.exit(SpringApplication.exit(context, () -> exit));
  }

  /** Runs the backfill, logs the counts and returns the process exit code. */
  int execute() {
    try {
      BackfillResult result = backfill.run();
      LOG.atInfo()
          .addKeyValue(
              LogKeys.STATUS,
              "embedded=%d skipped=%d truncated=%d"
                  .formatted(result.embedded(), result.skipped(), result.truncated()))
          .log("embedding backfill finished");
      return 0;
    } catch (RuntimeException e) {
      LOG.atError()
          .addKeyValue(LogKeys.REASON_CODE, e.getClass().getSimpleName())
          .log("embedding backfill failed");
      return 1;
    }
  }
}
