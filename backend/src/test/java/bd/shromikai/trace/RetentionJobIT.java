package bd.shromikai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.MutableClockConfig;
import bd.shromikai.testsupport.Requirement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * NFR-16: trace after 30 days, review items when expired, runs after 90 days (controllable clock).
 */
@IntegrationTest
@Import(MutableClockConfig.class)
@Requirement("NFR-16")
class RetentionJobIT {

  @Autowired JdbcClient jdbc;
  @Autowired RetentionJob job;

  @AfterEach
  void clean() {
    jdbc.sql("delete from analysis_run").update();
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  void rowsLeaveOnTheScheduleOfArchitectureSection14() {
    Instant now = MutableClockConfig.CLOCK.instant();
    UUID run = UUID.randomUUID();
    jdbc.sql(
            """
            insert into analysis_run (id, subject_kind, subject_hash, client_kind, side,
                                      output_language, input_kind, page_count, status,
                                      pipeline_version, created_at)
            values (:id, 'USER', repeat('c', 64), 'API', 'WORKER', 'en', 'TEXT', 1, 'SUCCEEDED',
                    'p7-1', :created)
            """)
        .param("id", run)
        .param("created", Timestamp.from(now))
        .update();
    jdbc.sql(
            "insert into analysis_trace (analysis_id, step, detail, created_at)"
                + " values (:id, 'INTAKE', '{}'::jsonb, :at)")
        .param("id", run)
        .param("at", Timestamp.from(now))
        .update();
    jdbc.sql(
            """
            insert into review_item (id, kind, analysis_id, finding_id, clause_type, reason_codes,
                                     status, created_at, expires_at)
            values (:id, 'FINDING', :run, 'f1', 'WAGES', array['NOT_VERIFIED'], 'OPEN', :at, :exp)
            """)
        .param("id", UUID.randomUUID())
        .param("run", run)
        .param("at", Timestamp.from(now))
        .param("exp", Timestamp.from(now.plus(Duration.ofDays(30))))
        .update();

    MutableClockConfig.CLOCK.advance(Duration.ofDays(29));
    assertThat(job.purge()).isEqualTo(new RetentionJob.Result(0, 0, 0));

    MutableClockConfig.CLOCK.advance(Duration.ofDays(2)); // day 31
    assertThat(job.purge()).isEqualTo(new RetentionJob.Result(1, 1, 0));
    assertThat(count("analysis_trace")).isZero();
    assertThat(count("review_item")).isZero();
    assertThat(count("analysis_run")).isEqualTo(1);

    MutableClockConfig.CLOCK.advance(Duration.ofDays(60)); // day 91
    assertThat(job.purge().runs()).isEqualTo(1);
    assertThat(count("analysis_run")).isZero();
  }
}
