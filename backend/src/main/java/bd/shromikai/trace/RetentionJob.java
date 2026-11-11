package bd.shromikai.trace;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces the retention schedule of architecture §14 (NFR-16) once a day: trace rows after 30
 * days, review items of kind {@code FINDING} when their {@code expires_at} has passed (30 days
 * after creation if undecided, 30 days after the decision), and analysis runs after 90 days. Rows
 * hold no contract text; the job logs counts only. Audit events are append-only and are not
 * touched.
 */
@Component
public class RetentionJob {

  static final Duration TRACE = Duration.ofDays(30);
  static final Duration RUN = Duration.ofDays(90);

  private static final Logger LOG = LoggerFactory.getLogger(RetentionJob.class);

  private final JdbcClient jdbc;
  private final Clock clock;

  public RetentionJob(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Counts of deleted rows. */
  public record Result(int traceRows, int reviewItems, int runs) {}

  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Dhaka")
  public void scheduled() {
    Result r = purge();
    LOG.info(
        "retention: trace={} reviewItems={} runs={}", r.traceRows(), r.reviewItems(), r.runs());
  }

  public Result purge() {
    Timestamp now = Timestamp.from(clock.instant());
    int trace =
        jdbc.sql("delete from analysis_trace where created_at < :cutoff")
            .param("cutoff", Timestamp.from(clock.instant().minus(TRACE)))
            .update();
    int items =
        jdbc.sql("delete from review_item where kind = 'FINDING' and expires_at <= :now")
            .param("now", now)
            .update();
    int runs =
        jdbc.sql("delete from analysis_run where created_at < :cutoff")
            .param("cutoff", Timestamp.from(clock.instant().minus(RUN)))
            .update();
    return new Result(trace, items, runs);
  }
}
