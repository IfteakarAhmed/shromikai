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
 * clauses, lives at most 30 minutes <em>after it completes</em> (FR-54), or 30 minutes after it was
 * created if it never completes, and is purged on expiry or delete. Nothing is written to disk or
 * the database (R-02).
 */
@Component
public final class JobStore {

  public static final Duration TTL = Duration.ofMinutes(30);

  private final Map<UUID, AnalysisJob> jobs = new ConcurrentHashMap<>();

  /** IDs of expired or deleted jobs with their owner key, so the owner gets 410, others 404. */
  private final Map<UUID, String> gone =
      java.util.Collections.synchronizedMap(
          new java.util.LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
              return size() > 10_000;
            }
          });

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
      AnalysisJob job = new AnalysisJob(UUID.randomUUID(), clock.instant(), clock);
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
      remember(job);
      job.purge();
    }
    return job != null;
  }

  /** True when the job existed, belonged to this subject and has expired or been deleted. */
  public boolean wasOwnedBy(UUID id, AnalysisSubject subject) {
    return subject.key().equals(gone.get(id));
  }

  private void remember(AnalysisJob job) {
    AnalysisSubject owner = job.owner();
    if (owner != null) {
      gone.put(job.id(), owner.key());
    }
  }

  /** Jobs still running (neither analysed nor failed) that satisfy the filter. */
  public long countRunning(java.util.function.Predicate<AnalysisJob> filter) {
    return jobs.values().stream().filter(j -> j.completedAt() == null).filter(filter).count();
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
              Instant since = job.completedAt() != null ? job.completedAt() : job.createdAt();
              if (since.isBefore(cutoff)) {
                remember(job);
                job.purge();
                return true;
              }
              return false;
            });
  }
}
