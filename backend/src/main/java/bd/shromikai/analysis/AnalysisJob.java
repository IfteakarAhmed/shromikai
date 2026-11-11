package bd.shromikai.analysis;

import bd.shromikai.privacy.PlaceholderMap;
import bd.shromikai.shared.problem.ProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One analysis job, held in memory only (R-02). It owns the {@link PlaceholderMap}, the redacted
 * clauses and the trace. Nothing in it is serialised or persisted; {@link #purge()} drops it all.
 */
public final class AnalysisJob {

  /**
   * {@code CLASSIFIED} ends the P5 steps; P6 continues to {@code ANALYSED} (through {@code
   * COMPOSE}); P7 adds the memo and the result handling.
   */
  public enum Status {
    RUNNING,
    CLASSIFIED,
    ANALYSED,
    FAILED
  }

  /** A warning shown with the result (api.md §5.12). */
  public record Warning(String code, String message) {}

  /**
   * What the P6 pipeline produced. Findings and redlines are redacted (placeholders, not names);
   * re-identification belongs to P7 ({@code AnalysisService.resultFor}).
   */
  public record Outcome(
      List<Finding> findings,
      List<Redline> redlines,
      List<Warning> warnings,
      int reviewItems,
      Map<String, Integer> promptVersions,
      Map<String, String> models,
      String verifierIndependence,
      String corpusRelease,
      long costUsdMicros,
      long durationMs,
      List<FindingVerifier.VerdictSummary> verdicts,
      List<ObligationItem> obligations,
      MemoNarrator.Narrative narrative,
      ReviewQueue.@Nullable Ticket ticket) {

    public Outcome {
      findings = List.copyOf(findings);
      redlines = List.copyOf(redlines);
      warnings = List.copyOf(warnings);
      promptVersions = Map.copyOf(promptVersions);
      models = Map.copyOf(models);
      verdicts = List.copyOf(verdicts);
      obligations = List.copyOf(obligations);
    }
  }

  private final UUID id;
  private final Instant createdAt;
  private final Clock clock;
  private volatile @Nullable Instant completedAt;
  private volatile @Nullable AnalysisSubject owner;
  private volatile @Nullable AnalysisRequest request;
  private final PlaceholderMap placeholders;
  private final List<TraceEvent> trace = Collections.synchronizedList(new ArrayList<>());
  private volatile Status status = Status.RUNNING;
  private volatile Stage stage = Stage.INTAKE;
  private volatile List<Clause> clauses = List.of();
  private volatile @Nullable ProblemException failure;
  private volatile @Nullable Outcome outcome;
  private volatile @Nullable Baseline baseline;
  private volatile String inputKind = "TEXT";
  private volatile int pageCount = 1;
  private volatile @Nullable String language;

  AnalysisJob(UUID id, Instant createdAt, Clock clock) {
    this.id = id;
    this.createdAt = createdAt;
    this.clock = clock;
    this.placeholders = new PlaceholderMap(id.toString());
  }

  public UUID id() {
    return id;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** When the job finished or failed; {@code null} while it runs. */
  public @Nullable Instant completedAt() {
    return completedAt;
  }

  /** The subject that asked; only that subject may read the job (T-07). */
  public @Nullable AnalysisSubject owner() {
    return owner;
  }

  public @Nullable AnalysisRequest request() {
    return request;
  }

  void owner(AnalysisSubject subject, AnalysisRequest asked) {
    this.owner = subject;
    this.request = asked;
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

  /** The analysis result of the P6 pipeline, or {@code null} before {@code ANALYSED}. */
  public @Nullable Outcome outcome() {
    return outcome;
  }

  /** The compare-mode baseline, or {@code null} until it has run (or when not asked for). */
  public @Nullable Baseline baseline() {
    return baseline;
  }

  void baseline(Baseline result) {
    this.baseline = result;
  }

  public String inputKind() {
    return inputKind;
  }

  public int pageCount() {
    return pageCount;
  }

  public @Nullable String language() {
    return language;
  }

  void intake(String kind, int pages, @Nullable String detectedLanguage) {
    this.inputKind = kind;
    this.pageCount = Math.max(1, pages);
    this.language = detectedLanguage;
  }

  void analysed(Outcome result) {
    this.outcome = result;
    this.completedAt = clock.instant();
    this.status = Status.ANALYSED;
    this.stage = Stage.COMPLETE;
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
    this.completedAt = clock.instant();
    this.status = Status.FAILED;
    this.clauses = List.of();
    this.outcome = null;
    this.placeholders.clear();
  }

  /** Drops the placeholder map and every clause (job expiry or delete). */
  void purge() {
    placeholders.clear();
    clauses = List.of();
    outcome = null;
    baseline = null;
    trace.clear();
  }
}
