package bd.shromikai.analysis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The in-memory job store (architecture §10.1): a job, with its placeholder map and redacted
 * clauses, lives at most 30 minutes and is purged on expiry or delete. Nothing is written to disk
 * or the database (R-02).
 */
@Component
public final class JobStore {

  public static final Duration TTL = Duration.ofMinutes(30);

  private final Map<UUID, AnalysisJob> jobs = new ConcurrentHashMap<>();
  private final Clock clock;

  public JobStore(Clock clock) {
    this.clock = clock;
  }

  AnalysisJob create() {
    purgeExpired();
    AnalysisJob job = new AnalysisJob(UUID.randomUUID(), clock.instant());
    jobs.put(job.id(), job);
    return job;
  }

  public Optional<AnalysisJob> find(UUID id) {
    purgeExpired();
    return Optional.ofNullable(jobs.get(id));
  }

  /** Removes a job now and drops its contents; returns whether it existed. */
  public boolean delete(UUID id) {
    AnalysisJob job = jobs.remove(id);
    if (job != null) {
      job.purge();
    }
    return job != null;
  }

  public int size() {
    return jobs.size();
  }

  void purgeExpired() {
    Instant cutoff = clock.instant().minus(TTL);
    jobs.values()
        .removeIf(
            job -> {
              if (job.createdAt().isBefore(cutoff)) {
                job.purge();
                return true;
              }
              return false;
            });
  }
}
