package bd.shromikai.analysis;

import bd.shromikai.analysis.CandidateRetriever.Result;
import bd.shromikai.analysis.RiskAgent.ClauseView;
import bd.shromikai.law.LawReadService;
import bd.shromikai.rules.ClauseFact;
import bd.shromikai.rules.RuleEngine;
import bd.shromikai.rules.RuleSet;
import bd.shromikai.rules.RuleViolation;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.shared.text.OverlapChecker;
import bd.shromikai.trace.AnalysisTraceStore;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifiedCitation;
import bd.shromikai.verify.VerifierIndependence;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The state machine after {@code CLASSIFY} (architecture §5.1): {@code RETRIEVE} in parallel with
 * {@code RULES}, then {@code RISK}, {@code VERIFY}, {@code CORRECT} (only when a citation is not
 * verified), {@code COMPOSE} (counter-offers) and {@code COMPLETE}. {@code FAILED} is reachable
 * from every state. Each transition writes one content-free trace event, in memory and in {@code
 * analysis_trace}. Every step has a timeout, the whole analysis a wall-time limit, and every model
 * call counts against the job's token and cost budget. A verifier that cannot be reached degrades a
 * citation to {@code UNCERTAIN}; it never produces a {@code VERIFIED} one. P7 adds the memo,
 * obligations and result handling after {@code COMPOSE}.
 */
@Service
public class AnalysisPipeline {

  public static final String PIPELINE_VERSION = "p6-1";

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisPipeline.class);
  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  /** A failure tagged with the state it happened in. */
  private static final class StageFailure extends RuntimeException {
    final transient Stage stage;
    final transient ProblemException problem;

    StageFailure(Stage stage, ProblemException problem) {
      super(problem.type().code(), null, false, false);
      this.stage = stage;
      this.problem = problem;
    }
  }

  private final FactExtractor facts;
  private final RuleEngine engine;
  private final RuleSet rules;
  private final CandidateRetriever retriever;
  private final RiskAgent risk;
  private final FindingMerge merge;
  private final FindingVerifier verifier;
  private final CorrectiveLoop corrective;
  private final ReviewQueue reviews;
  private final CounterOfferAgent counterOffers;
  private final ModelGateway gateway;
  private final CostCalculator cost;
  private final LawReadService law;
  private final AnalysisRunStore runs;
  private final AnalysisTraceStore traces;
  private final AnalysisProperties properties;
  private final Clock clock;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public AnalysisPipeline(
      FactExtractor facts,
      RuleEngine engine,
      RuleSet rules,
      CandidateRetriever retriever,
      RiskAgent risk,
      FindingMerge merge,
      FindingVerifier verifier,
      CorrectiveLoop corrective,
      ReviewQueue reviews,
      CounterOfferAgent counterOffers,
      ModelGateway gateway,
      CostCalculator cost,
      LawReadService law,
      AnalysisRunStore runs,
      AnalysisTraceStore traces,
      AnalysisProperties properties,
      Clock clock) {
    this.facts = facts;
    this.engine = engine;
    this.rules = rules;
    this.retriever = retriever;
    this.risk = risk;
    this.merge = merge;
    this.verifier = verifier;
    this.corrective = corrective;
    this.reviews = reviews;
    this.counterOffers = counterOffers;
    this.gateway = gateway;
    this.cost = cost;
    this.law = law;
    this.runs = runs;
    this.traces = traces;
    this.properties = properties;
    this.clock = clock;
  }

  /** Continues a job that finished {@code CLASSIFY}; ends it {@code ANALYSED} or {@code FAILED}. */
  public void run(AnalysisJob job, AnalysisRequest request, AnalysisSubject subject) {
    if (job.status() != AnalysisJob.Status.CLASSIFIED) {
      throw new IllegalStateException("The job has not finished CLASSIFY");
    }
    long started = clock.millis();
    String release = law.corpusRelease();
    runs.start(job, request, subject, PIPELINE_VERSION, release);
    for (TraceEvent earlier : job.trace()) {
      persist(job, earlier);
    }
    ModelSession session =
        new ModelSession(
            gateway,
            cost,
            job.placeholders(),
            properties.budget().perAnalysisUsdMicros(),
            properties.budget().perAnalysisTokens());
    Deadline deadline = new Deadline(started + properties.analysis().hardTimeout().toMillis());
    try {
      JobData data = new JobData(job, request, session, deadline, release);
      pipeline(data);
    } catch (StageFailure failure) {
      fail(job, failure.stage, failure.problem, session, started);
    } catch (ProblemException problem) {
      fail(job, job.stage(), problem, session, started);
    } catch (RuntimeException unexpected) {
      LOG.error(
          "analysis step failed unexpectedly: "
              + unexpected.getClass().getSimpleName()
              + " at "
              + (unexpected.getStackTrace().length > 0 ? unexpected.getStackTrace()[0] : "?"));
      fail(job, job.stage(), new ProblemException(ProblemType.INTERNAL_ERROR), session, started);
    }
  }

  // ------------------------------------------------------------------ pipeline

  /** Everything the steps share. */
  private static final class JobData {
    final AnalysisJob job;
    final AnalysisRequest request;
    final ModelSession session;
    final Deadline deadline;
    final @Nullable String release;
    final long started;
    final List<AnalysisJob.Warning> warnings = new ArrayList<>();
    final Map<String, Integer> promptVersions = new TreeMap<>();
    final Map<String, String> models = new TreeMap<>();
    final Set<VerifierIndependence> independence = new LinkedHashSet<>();
    final Map<Stage, ModelSession.StageUsage> reported = new EnumMap<>(Stage.class);
    final List<FindingVerifier.VerdictSummary> verdicts = new ArrayList<>();

    JobData(
        AnalysisJob job,
        AnalysisRequest request,
        ModelSession session,
        Deadline deadline,
        @Nullable String release) {
      this.job = job;
      this.request = request;
      this.session = session;
      this.deadline = deadline;
      this.release = release;
      this.started = System.currentTimeMillis();
    }
  }

  private void pipeline(JobData d) {
    List<Clause> clauses = d.job.clauses();
    Map<String, Clause> byId = new LinkedHashMap<>();
    clauses.forEach(c -> byId.put(c.id(), c));

    // RETRIEVE in parallel with RULES.
    d.job.enter(Stage.RETRIEVE);
    long t0 = clock.millis();
    Future<Result> retrieveFuture =
        executor.submit(() -> retriever.retrieve(clauses, d.session.egress()));
    Future<Map<String, List<ClauseFact>>> factsFuture =
        executor.submit(() -> facts.extract(d.session, Stage.RULES, clauses));
    Result retrieved =
        await(retrieveFuture, Stage.RETRIEVE, d, properties.timeoutOf(Stage.RETRIEVE), factsFuture);
    CandidateSet candidates = retrieved.candidates();
    record(
        d,
        Stage.RETRIEVE,
        clock.millis() - t0,
        detail(
            "clausesSearched", byIdSearched(clauses, candidates),
            "candidateSections", candidates.size(),
            "keywordOnly", retrieved.keywordOnly()));
    if (retrieved.keywordOnly()) {
      d.warnings.add(
          new AnalysisJob.Warning(
              "retrieval-keyword-only",
              "The embedding model was unavailable; keyword search only."));
    }
    d.job.enter(Stage.RULES);
    Map<String, List<ClauseFact>> factsByClause =
        await(factsFuture, Stage.RULES, d, properties.timeoutOf(Stage.RULES), retrieveFuture);
    RuleEngine.Evaluation evaluation =
        engine.evaluate(
            factsByClause.entrySet().stream()
                .map(e -> new RuleEngine.ClauseFacts(e.getKey(), e.getValue()))
                .toList(),
            rules.active(),
            null);
    record(
        d,
        Stage.RULES,
        clock.millis() - t0,
        detail(
            "facts", factsByClause.values().stream().mapToInt(List::size).sum(),
            "activeRules", rules.active().size(),
            "draftRules", rules.draftCount(),
            "violations", evaluation.violations().size(),
            "notes", evaluation.notes().stream().map(RuleEngine.Note::code).distinct().toList()));

    // RISK.
    d.job.enter(Stage.RISK);
    long riskStart = clock.millis();
    List<Finding> ruleFindings =
        RuleFindings.of(evaluation.violations(), d.request.outputLanguage());
    List<ClauseView> views = new ArrayList<>();
    for (Clause clause : clauses) {
      views.add(new ClauseView(clause, factsByClause.getOrDefault(clause.id(), List.of())));
    }
    RiskAgent.Result riskResult =
        step(
            Stage.RISK,
            d,
            () ->
                risk.run(
                    d.session,
                    d.request,
                    views,
                    candidates,
                    ruleFindingJson(evaluation.violations()),
                    List.of()));
    FindingMerge.Result merged = merge.merge(ruleFindings, riskResult.findings());
    List<Finding> findings = merged.findings();
    record(
        d,
        Stage.RISK,
        clock.millis() - riskStart,
        detail(
            "batches", riskResult.batches(),
            "splits", riskResult.splits(),
            "llmFindings", riskResult.findings().size(),
            "ruleFindings", ruleFindings.size(),
            "findings", findings.size(),
            "severityConflicts", merged.conflicts().size(),
            "claimedSectionKeys", List.copyOf(RiskAgent.citedKeys(findings))));

    // VERIFY.
    d.job.enter(Stage.VERIFY);
    long verifyStart = clock.millis();
    VerificationContext context =
        new VerificationContext(
            clock.instant().atZone(DHAKA).toLocalDate(),
            properties.analysis().jurisdictions(),
            d.release == null ? "none" : d.release,
            candidates.keys(),
            1,
            d.session.egress(),
            new OverlapChecker(clauses.stream().map(c -> c.text().reveal()).toList()));
    List<Finding> toVerify = findings;
    FindingVerifier.Round verified =
        step(Stage.VERIFY, d, () -> verifier.verify(toVerify, context));
    findings = verified.findings();
    d.verdicts.addAll(verified.verdicts());
    noteIndependence(d, findings);
    record(
        d,
        Stage.VERIFY,
        clock.millis() - verifyStart,
        detail(
            "verdicts", verdictJson(verified.verdicts()),
            "verifiedFindings", count(findings, FindingVerification.VERIFIED),
            "uncertainFindings", count(findings, FindingVerification.UNCERTAIN)));

    // CORRECT, only when a citation is not verified.
    if (corrective.needed(findings)) {
      d.job.enter(Stage.CORRECT);
      long correctStart = clock.millis();
      List<Finding> toCorrect = findings;
      CorrectiveLoop.Round corrected =
          step(
              Stage.CORRECT,
              d,
              () ->
                  corrective.correct(
                      d.session, d.request, toCorrect, clauses, candidates, context));
      findings = corrected.findings();
      corrected.corrections().stream()
          .filter(c -> c.verdict() != null)
          .forEach(c -> d.verdicts.add(c.verdict()));
      noteIndependence(d, findings);
      record(
          d,
          Stage.CORRECT,
          clock.millis() - correctStart,
          detail(
              "corrections",
              corrected.corrections().stream()
                  .map(
                      c ->
                          Map.<String, Object>of(
                              "finding", c.findingId(),
                              "repaired", c.repaired(),
                              "newSections", c.newSections(),
                              "abstained", c.abstained()))
                  .toList(),
              "verifiedFindings",
              count(findings, FindingVerification.VERIFIED),
              "uncertainFindings",
              count(findings, FindingVerification.UNCERTAIN)));
    }

    // Review items for UNCERTAIN findings and rule/LLM conflicts.
    ReviewQueue.Opened opened =
        reviews.open(
            d.job.id(),
            d.request.side(),
            findings,
            merged.conflicts(),
            byId,
            factsByClause,
            candidates.keys());
    findings = opened.findings();

    // COMPOSE: counter-offers for VERIFIED findings (the memo is P7).
    d.job.enter(Stage.COMPOSE);
    long composeStart = clock.millis();
    List<Finding> toPropose = findings;
    List<CounterOfferAgent.ForFinding> proposals =
        step(
            Stage.COMPOSE,
            d,
            () -> counterOffers.proposeAll(d.session, d.request, toPropose, byId, rules.active()));
    List<Redline> redlines = new ArrayList<>();
    int dropped = 0;
    Map<String, String> redlineByFinding = new LinkedHashMap<>();
    for (CounterOfferAgent.ForFinding proposal : proposals) {
      if (proposal.outcome().redline() != null) {
        redlines.add(proposal.outcome().redline());
        redlineByFinding.put(proposal.findingId(), proposal.outcome().redline().id());
      } else {
        dropped++;
        d.warnings.add(
            new AnalysisJob.Warning(
                "counter-offer-dropped",
                "A suggested revision was dropped: " + proposal.outcome().droppedBecause() + "."));
      }
    }
    if (proposals.stream().anyMatch(p -> p.outcome().unchecked())) {
      d.warnings.add(
          new AnalysisJob.Warning(
              "counter-offer-unchecked",
              "No active rule applied, so the suggested revisions were not checked against a"
                  + " legal limit."));
    }
    List<Finding> withRedlines = new ArrayList<>();
    for (Finding finding : findings) {
      String redline = redlineByFinding.get(finding.id());
      withRedlines.add(redline == null ? finding : finding.withRedline(redline));
    }
    findings = withRedlines;
    record(
        d,
        Stage.COMPOSE,
        clock.millis() - composeStart,
        detail(
            "redlines", redlines.size(),
            "dropped", dropped,
            "skippedUncertain", count(findings, FindingVerification.UNCERTAIN),
            "reviewItems", opened.items().size()));

    // COMPLETE.
    if (findings.size() < 3) {
      d.warnings.add(
          new AnalysisJob.Warning(
              "fewer-than-three-findings",
              "Fewer than three risks were found; none were invented."));
    }
    if (d.independence.contains(VerifierIndependence.SAME_FAMILY)) {
      d.warnings.add(
          new AnalysisJob.Warning(
              "verifier-same-family", "Citations were checked by a model of the same family."));
    }
    if (clauses.stream().anyMatch(c -> c.flags().contains(ClauseFlag.INSTRUCTION_LIKE_TEXT))) {
      d.warnings.add(
          new AnalysisJob.Warning(
              "instruction-like-text",
              "A clause looked like an instruction and was analysed as data."));
    }
    long durationMs =
        clock.millis() - (d.deadline.end - properties.analysis().hardTimeout().toMillis());
    int verifiedCitations = findings.stream().mapToInt(f -> f.citations().size()).sum();
    long verifiedFindings = count(findings, FindingVerification.VERIFIED);
    long uncertain = count(findings, FindingVerification.UNCERTAIN);
    collectProvenance(d);
    String independence = independenceLabel(d.independence);
    AnalysisJob.Outcome outcome =
        new AnalysisJob.Outcome(
            findings,
            redlines,
            d.warnings,
            opened.items().size(),
            d.promptVersions,
            d.models,
            independence,
            d.release == null ? "none" : d.release,
            d.session.costUsdMicros(),
            durationMs,
            d.verdicts);
    d.job.analysed(outcome);
    record(
        d,
        Stage.COMPLETE,
        0,
        detail(
            "findings", findings.size(),
            "verifiedFindings", verifiedFindings,
            "uncertainFindings", uncertain,
            "verifiedCitations", verifiedCitations,
            "costUsdMicros", d.session.costUsdMicros(),
            "durationMs", durationMs));
    long tokensIn = 0;
    long tokensOut = 0;
    for (Stage stage : Stage.values()) {
      tokensIn += d.session.usage(stage).tokensIn();
      tokensOut += d.session.usage(stage).tokensOut();
    }
    runs.succeeded(
        d.job,
        findings.size(),
        verifiedCitations,
        (int) uncertain,
        d.promptVersions,
        d.models,
        d.session.costUsdMicros(),
        tokensIn,
        tokensOut,
        durationMs,
        clock.instant());
  }

  // ------------------------------------------------------------------ helpers

  private <T> T step(Stage stage, JobData d, Callable<T> body) {
    Future<T> future = executor.submit(body);
    return await(future, stage, d, properties.timeoutOf(stage));
  }

  private <T> T await(
      Future<T> future, Stage stage, JobData d, java.time.Duration timeout, Future<?>... siblings) {
    long limit = Math.min(timeout.toMillis(), Math.max(1, d.deadline.remaining()));
    try {
      return future.get(limit, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      for (Future<?> sibling : siblings) {
        sibling.cancel(true);
      }
      throw new StageFailure(stage, new ProblemException(ProblemType.ANALYSIS_TIMEOUT));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new StageFailure(stage, new ProblemException(ProblemType.INTERNAL_ERROR));
    } catch (ExecutionException e) {
      for (Future<?> sibling : siblings) {
        sibling.cancel(true);
      }
      Throwable cause = e.getCause();
      if (cause instanceof ProblemException problem) {
        throw new StageFailure(stage, problem);
      }
      // Class and first frame only: never a message, which could hold text (R-02). The log
      // formatter prints the message string as built, so the details go into it.
      LOG.error(
          "analysis step "
              + stage
              + " failed: "
              + cause.getClass().getSimpleName()
              + " at "
              + (cause.getStackTrace().length > 0 ? cause.getStackTrace()[0] : "?"));
      throw new StageFailure(stage, new ProblemException(ProblemType.INTERNAL_ERROR));
    }
  }

  private void record(JobData d, Stage stage, long durationMs, Map<String, Object> detail) {
    ModelSession.StageUsage usage = d.session.usage(stage);
    boolean model = usage.calls() > 0;
    TraceEvent event =
        new TraceEvent(
            stage,
            clock.instant(),
            durationMs,
            TraceEvent.OK,
            model ? usage.promptId() : null,
            model && usage.promptVersion() > 0 ? usage.promptVersion() : null,
            model ? usage.modelId() : null,
            detail,
            model ? usage.tokensIn() : null,
            model ? usage.tokensOut() : null,
            model ? usage.costMicros() : null);
    d.job.record(event);
    persist(d.job, event);
  }

  private void persist(AnalysisJob job, TraceEvent e) {
    traces.append(
        job.id(),
        e.stage().name(),
        e.at(),
        e.durationMs(),
        e.outcome(),
        e.promptId(),
        e.promptVersion(),
        e.modelId(),
        e.tokensIn(),
        e.tokensOut(),
        e.costUsdMicros(),
        e.detail());
  }

  private void fail(
      AnalysisJob job, Stage stage, ProblemException problem, ModelSession session, long started) {
    String reason =
        problem instanceof ModelException model ? model.reasonCode() : problem.type().code();
    TraceEvent event =
        new TraceEvent(
            stage,
            clock.instant(),
            clock.millis() - started,
            TraceEvent.FAILED,
            null,
            null,
            null,
            Map.of(
                "problem", problem.type().code(), "reason", reason.replaceAll("[^a-z0-9-]", "-")),
            null,
            null,
            session.costUsdMicros());
    job.record(event);
    try {
      persist(job, event);
      runs.failed(
          job,
          problem.type().code(),
          session.costUsdMicros(),
          clock.millis() - started,
          clock.instant());
    } catch (RuntimeException storeFailure) {
      LOG.error("could not store the failed run: {}", storeFailure.getClass().getSimpleName());
    }
    job.fail(problem);
  }

  private static int byIdSearched(List<Clause> clauses, CandidateSet candidates) {
    return (int) clauses.stream().filter(c -> !candidates.keysOf(c.id()).isEmpty()).count();
  }

  private static long count(List<Finding> findings, FindingVerification status) {
    return findings.stream().filter(f -> f.verification() == status).count();
  }

  private static List<Map<String, Object>> ruleFindingJson(List<RuleViolation> violations) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (RuleViolation v : violations) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("clauseId", v.clauseId());
      item.put("ruleId", v.rule().id());
      item.put("fact", v.rule().fact().name());
      item.put(
          "limit", v.rule().comparator().name() + " " + v.rule().value() + " " + v.rule().unit());
      item.put("observed", v.observed());
      item.put("sectionKey", v.rule().sectionKey());
      item.put("severity", v.rule().severity().name());
      out.add(item);
    }
    return out;
  }

  private static List<Map<String, Object>> verdictJson(List<FindingVerifier.VerdictSummary> list) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (FindingVerifier.VerdictSummary v : list.subList(0, Math.min(40, list.size()))) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("finding", v.findingId());
      item.put("sectionKey", v.sectionKey());
      item.put("status", v.status().name());
      item.put("reasons", v.reasonCodes());
      item.put("attempt", v.attempt());
      out.add(item);
    }
    return out;
  }

  private static void noteIndependence(JobData d, List<Finding> findings) {
    for (Finding f : findings) {
      for (VerifiedCitation c : f.citations()) {
        VerifierIndependence i = c.verification().checks().support().independence();
        if (i != null) {
          d.independence.add(i);
        }
      }
    }
  }

  private void collectProvenance(JobData d) {
    for (Stage stage : Stage.values()) {
      ModelSession.StageUsage usage = d.session.usage(stage);
      if (usage.calls() > 0 && usage.promptId() != null && usage.promptVersion() > 0) {
        d.promptVersions.put(usage.promptId(), usage.promptVersion());
        d.models.put(usage.promptId(), usage.modelId());
      }
    }
  }

  private static String independenceLabel(Set<VerifierIndependence> seen) {
    boolean different = seen.contains(VerifierIndependence.DIFFERENT_FAMILY);
    boolean same = seen.contains(VerifierIndependence.SAME_FAMILY);
    if (different && same) {
      return "MIXED";
    }
    if (different) {
      return "DIFFERENT_FAMILY";
    }
    if (same) {
      return "SAME_FAMILY";
    }
    return "NONE";
  }

  private static Map<String, Object> detail(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  /** The wall-time limit of one analysis. */
  private static final class Deadline {
    final long end;

    Deadline(long end) {
      this.end = end;
    }

    long remaining() {
      return end - System.currentTimeMillis();
    }
  }
}
