package bd.shromikai.analysis;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
  private final int maxJobs;

  public JobStore(Clock clock) {
    this(clock, 200);
  }

  @Autowired
  public JobStore(Clock clock, @Value("${shromikai.analysis.max-jobs}") int maxJobs) {
    this.clock = clock;
    this.maxJobs = maxJobs;
  }

  AnalysisJob create() {
    purgeExpired();
    // Reserve the slot atomically so concurrent creates cannot overshoot the cap.
    synchronized (jobs) {
      if (jobs.size() >= maxJobs) {
        throw new ProblemException(
            ProblemType.CAPACITY_EXHAUSTED, "Too many analyses are running. Try again shortly.");
      }
      AnalysisJob job = new AnalysisJob(UUID.randomUUID(), clock.instant());
      jobs.put(job.id(), job);
      return job;
    }
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

  /**
   * Drops expired jobs even when nobody is calling the store (R-02: bounded lifetime in memory).
   */
  @Scheduled(fixedDelayString = "${shromikai.analysis.job-sweep-interval:60s}")
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
