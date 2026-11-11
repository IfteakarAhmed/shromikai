package bd.shromikai.law;

import bd.shromikai.law.internal.IngestOrchestrator;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entry point of statute ingestion for the admin API ({@code POST /api/v1/admin/laws/ingest}) and
 * the {@code ingestLaws} task (FR-15). One run at a time per database: a PostgreSQL advisory lock
 * is held for the whole run, so a second request gets {@code 409 ingest-running}.
 */
@Service
public final class LawIngestService {

  private static final Logger LOG = LoggerFactory.getLogger(LawIngestService.class);

  /** Arbitrary constant naming the ingestion lock. */
  static final long LOCK_KEY = 727_001L;

  private final DataSource dataSource;
  private final IngestOrchestrator orchestrator;
  private final AtomicReference<@Nullable IngestOutcome> last = new AtomicReference<>();

  public LawIngestService(DataSource dataSource, IngestOrchestrator orchestrator) {
    this.dataSource = dataSource;
    this.orchestrator = orchestrator;
  }

  /** Runs now, on the calling thread. */
  public IngestOutcome run(@Nullable Set<String> acts) {
    try (Connection lock = dataSource.getConnection()) {
      acquire(lock);
      try {
        return remember(orchestrator.run(acts));
      } finally {
        release(lock);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("ingestion lock failed", e);
    }
  }

  /** Starts a run on a virtual thread and returns its id; the outcome is the quality report. */
  public UUID startAsync(@Nullable Set<String> acts) {
    Connection lock;
    try {
      lock = dataSource.getConnection();
      acquire(lock);
    } catch (SQLException e) {
      throw new IllegalStateException("ingestion lock failed", e);
    }
    UUID runId = UUID.randomUUID();
    Thread.ofVirtual()
        .name("ingest-" + runId)
        .start(
            () -> {
              try {
                remember(orchestrator.run(acts));
              } catch (RuntimeException e) {
                LOG.error("ingestion run failed", e);
              } finally {
                try {
                  release(lock);
                  lock.close();
                } catch (SQLException e) {
                  LOG.warn("could not release the ingestion lock");
                }
              }
            });
    return runId;
  }

  public @Nullable IngestOutcome lastOutcome() {
    return last.get();
  }

  private IngestOutcome remember(IngestOutcome outcome) {
    last.set(outcome);
    return outcome;
  }

  private static void acquire(Connection connection) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select pg_try_advisory_lock(?)")) {
      statement.setLong(1, LOCK_KEY);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next() || !rs.getBoolean(1)) {
          connection.close();
          throw new ProblemException(ProblemType.INGEST_RUNNING);
        }
      }
    }
  }

  private static void release(Connection connection) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select pg_advisory_unlock(?)")) {
      statement.setLong(1, LOCK_KEY);
      statement.execute();
    }
  }
}
