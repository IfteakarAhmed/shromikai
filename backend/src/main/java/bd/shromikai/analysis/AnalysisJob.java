package bd.shromikai.analysis;

import bd.shromikai.privacy.PlaceholderMap;
import bd.shromikai.shared.problem.ProblemException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One analysis job, held in memory only (R-02). It owns the {@link PlaceholderMap}, the redacted
 * clauses and the trace. Nothing in it is serialised or persisted; {@link #purge()} drops it all.
 */
public final class AnalysisJob {

  /** P5 ends at {@code CLASSIFIED}; P6 continues from there. */
  public enum Status {
    RUNNING,
    CLASSIFIED,
    FAILED
  }

  private final UUID id;
  private final Instant createdAt;
  private final PlaceholderMap placeholders;
  private final List<TraceEvent> trace = Collections.synchronizedList(new ArrayList<>());
  private volatile Status status = Status.RUNNING;
  private volatile Stage stage = Stage.INTAKE;
  private volatile List<Clause> clauses = List.of();
  private volatile @Nullable ProblemException failure;

  AnalysisJob(UUID id, Instant createdAt) {
    this.id = id;
    this.createdAt = createdAt;
    this.placeholders = new PlaceholderMap(id.toString());
  }

  public UUID id() {
    return id;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** The placeholder map; package-private so that only the analysis module touches it (§10.4). */
  PlaceholderMap placeholders() {
    return placeholders;
  }

  public Status status() {
    return status;
  }

  public Stage stage() {
    return stage;
  }

  /** Redacted clauses with their types; empty until {@code CLASSIFY} has finished. */
  public List<Clause> clauses() {
    return clauses;
  }

  public List<TraceEvent> trace() {
    synchronized (trace) {
      return List.copyOf(trace);
    }
  }

  /** The problem that stopped the job, or {@code null}. */
  public @Nullable ProblemException failure() {
    return failure;
  }

  void enter(Stage next) {
    this.stage = next;
  }

  void record(TraceEvent event) {
    trace.add(event);
  }

  void classified(List<Clause> result) {
    this.clauses = List.copyOf(result);
    this.status = Status.CLASSIFIED;
  }

  void fail(ProblemException problem) {
    this.failure = problem;
    this.status = Status.FAILED;
    this.clauses = List.of();
    this.placeholders.clear();
  }

  /** Drops the placeholder map and every clause (job expiry or delete). */
  void purge() {
    placeholders.clear();
    clauses = List.of();
    trace.clear();
  }
}
