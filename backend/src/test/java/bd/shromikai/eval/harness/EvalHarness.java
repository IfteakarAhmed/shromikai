package bd.shromikai.eval.harness;

import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisPipeline;
import bd.shromikai.analysis.AnalysisProperties;
import bd.shromikai.analysis.AnalysisRequest;
import bd.shromikai.analysis.AnalysisService;
import bd.shromikai.analysis.AnalysisSubject;
import bd.shromikai.analysis.Baseline;
import bd.shromikai.analysis.BaselineAgent;
import bd.shromikai.analysis.Clause;
import bd.shromikai.analysis.Finding;
import bd.shromikai.analysis.FindingSource;
import bd.shromikai.analysis.JobStore;
import bd.shromikai.analysis.ObligationItem;
import bd.shromikai.analysis.Side;
import bd.shromikai.analysis.TraceEvent;
import bd.shromikai.eval.AdversarialCase;
import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.CitationCase;
import bd.shromikai.eval.EvalResults.ClaimRecord;
import bd.shromikai.eval.EvalResults.ClauseRecord;
import bd.shromikai.eval.EvalResults.PredictedFinding;
import bd.shromikai.eval.EvalResults.ShownCitation;
import bd.shromikai.law.LawReadService;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.ai.fake.RecordedCall;
import bd.shromikai.verify.CitationOrigin;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.ReasonCode;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifiedCitation;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Runs one contract through the real system (intake, local redaction, classification, the full
 * pipeline for {@code shromikai}, the plain-LLM baseline for {@code baseline}) and verifies
 * adversarial citations through the real verifier, turning each job into a content-free {@link
 * AnalysisCase}. Which models answer (real, recorded or scripted stand-ins) is decided by the
 * Spring context around it, not here.
 */
public class EvalHarness {

  private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  private final AnalysisService analyses;
  private final AnalysisPipeline pipeline;
  private final BaselineAgent baseline;
  private final CitationVerifier verifier;
  private final LawReadService law;
  private final AnalysisProperties properties;
  private final JobStore jobs;
  private final Clock clock;
  private final ObjectProvider<FakeChatPort> fake;
  private volatile @Nullable String piiMode;

  public EvalHarness(
      AnalysisService analyses,
      AnalysisPipeline pipeline,
      BaselineAgent baseline,
      CitationVerifier verifier,
      LawReadService law,
      AnalysisProperties properties,
      JobStore jobs,
      Clock clock,
      ObjectProvider<FakeChatPort> fake) {
    this.analyses = analyses;
    this.pipeline = pipeline;
    this.baseline = baseline;
    this.verifier = verifier;
    this.law = law;
    this.properties = properties;
    this.jobs = jobs;
    this.clock = clock;
    this.fake = fake;
  }

  /** How the local PII step answered for the next run (recorded on every case). */
  public void piiMode(@Nullable String mode) {
    this.piiMode = mode;
  }

  /** Cloud calls of one step: from the live capture, or from the fake port's call log. */
  private final class Calls {
    private final int captureMark = CloudCapture.mark();
    private final int fakeMark = fakeCalls().size();

    List<CloudCapture.Call> taken(String label, List<String> canaries) {
      if (CloudCapture.active()) {
        return CloudCapture.since(captureMark);
      }
      List<RecordedCall> all = fakeCalls();
      List<CloudCapture.Call> out = new ArrayList<>();
      for (RecordedCall c : all.subList(Math.min(fakeMark, all.size()), all.size())) {
        if (c.request().role() == ModelRole.LOCAL_PII) {
          continue;
        }
        out.add(
            new CloudCapture.Call(
                label, c.promptId(), true, 0, CloudCapture.containsCanary(c.request(), canaries)));
      }
      return out;
    }
  }

  private List<RecordedCall> fakeCalls() {
    FakeChatPort port = fake.getIfAvailable();
    return port == null ? List.of() : port.calls();
  }

  /**
   * Runs the systems on one contract. P5 calls (classification) are counted with the first system
   * listed, since every system needs them.
   */
  public List<AnalysisCase> run(
      String contractId, String text, Side side, List<String> systems, List<String> canaries) {
    CloudCapture.canaries(canaries);
    AnalysisSubject subject = AnalysisSubject.dev();
    AnalysisRequest request = AnalysisRequest.of(side);
    List<AnalysisCase> out = new ArrayList<>();
    CloudCapture.label(contractId + ":" + systems.get(0));
    Calls classify = new Calls();
    long started = clock.millis();
    AnalysisJob job = analyses.open(subject, request);
    try {
      analyses.executeText(job, text);
      long classifiedAt = clock.millis();
      List<CloudCapture.Call> shared = classify.taken(contractId + ":shared", canaries);
      boolean first = true;
      for (String system : systems) {
        CloudCapture.label(contractId + ":" + system);
        List<CloudCapture.Call> extra = first ? shared : List.of();
        first = false;
        if (job.status() != AnalysisJob.Status.CLASSIFIED
            && job.status() != AnalysisJob.Status.ANALYSED) {
          out.add(failed(contractId, system, side, job, extra));
          continue;
        }
        Calls calls = new Calls();
        switch (system) {
          case "shromikai" -> {
            pipeline.run(job, request, subject);
            List<CloudCapture.Call> taken = concat(extra, calls.taken(system, canaries));
            out.add(shromikai(contractId, side, job, taken));
          }
          case "baseline" -> {
            long t0 = clock.millis();
            baseline.run(job);
            long ms = clock.millis() - t0 + (classifiedAt - started);
            List<CloudCapture.Call> taken = concat(extra, calls.taken(system, canaries));
            out.add(baseline(contractId, side, job, taken, ms));
          }
          default -> throw new IllegalArgumentException("Unknown system " + system);
        }
      }
    } finally {
      jobs.delete(job.id());
    }
    return out;
  }

  private static List<CloudCapture.Call> concat(
      List<CloudCapture.Call> a, List<CloudCapture.Call> b) {
    List<CloudCapture.Call> out = new ArrayList<>(a);
    out.addAll(b);
    return out;
  }

  private AnalysisCase failed(
      String contractId, String system, Side side, AnalysisJob job, List<CloudCapture.Call> calls) {
    return withCalls(
            AnalysisCase.builder(contractId, system, side.name())
                .outcome("FAILED")
                .failureCode(
                    job.failure() == null ? job.status().name() : job.failure().type().code())
                .language(job.language())
                .stageMs(stages(job)),
            calls)
        .build();
  }

  private static bd.shromikai.eval.EvalResults.Builder withCalls(
      bd.shromikai.eval.EvalResults.Builder b, List<CloudCapture.Call> calls) {
    int live = (int) calls.stream().filter(c -> !c.replayed()).count();
    return b.cloudRequests(calls.size())
        .canaryHits((int) calls.stream().filter(CloudCapture.Call::canaryHit).count())
        .liveCalls(live)
        .replayedCalls(calls.size() - live)
        .liveCostMicros(
            calls.stream()
                .filter(c -> !c.replayed())
                .mapToLong(CloudCapture.Call::costMicros)
                .sum())
        .recorded(!calls.isEmpty() && live == 0);
  }

  private static Map<String, Long> stages(AnalysisJob job) {
    Map<String, Long> out = new TreeMap<>();
    for (TraceEvent e : job.trace()) {
      out.merge(e.stage().name(), e.durationMs(), Long::sum);
    }
    return out;
  }

  private static List<ClauseRecord> clauses(AnalysisJob job) {
    List<ClauseRecord> out = new ArrayList<>();
    for (Clause c : job.clauses()) {
      out.add(
          new ClauseRecord(
              c.label() == null ? "" : c.label().strip(),
              c.type() == null ? "UNCLASSIFIED" : c.type().name()));
    }
    return out;
  }

  private AnalysisCase shromikai(
      String contractId, Side side, AnalysisJob job, List<CloudCapture.Call> calls) {
    var b =
        AnalysisCase.builder(contractId, "shromikai", side.name())
            .piiMode(piiMode)
            .language(job.language())
            .stageMs(stages(job))
            .clauses(clauses(job));
    withCalls(b, calls);
    if (job.status() != AnalysisJob.Status.ANALYSED || job.outcome() == null) {
      return b.outcome("FAILED")
          .failureCode(job.failure() == null ? job.status().name() : job.failure().type().code())
          .build();
    }
    var outcome = job.outcome();
    Map<String, String> labelOf = new HashMap<>();
    job.clauses().forEach(c -> labelOf.put(c.id(), c.label() == null ? "" : c.label().strip()));
    List<PredictedFinding> findings = new ArrayList<>();
    Map<String, ShownCitation> shown = new LinkedHashMap<>();
    Map<String, String> originOf = new HashMap<>();
    for (Finding f : outcome.findings()) {
      originOf.put(f.id(), f.source() == FindingSource.RULE_ENGINE ? "RULE" : "LLM");
      findings.add(
          new PredictedFinding(
              f.id(),
              f.clauseIds().stream().map(id -> labelOf.getOrDefault(id, id)).toList(),
              f.clauseType(),
              f.kind().name(),
              f.severity().name(),
              f.verification().name(),
              f.confidence(),
              f.citations().stream().map(VerifiedCitation::sectionKey).toList(),
              f.source().name()));
      f.citations().forEach(c -> shown.putIfAbsent(c.citationId(), shownOf(c)));
    }
    for (ObligationItem o : outcome.obligations()) {
      o.citations().forEach(c -> shown.putIfAbsent(c.citationId(), shownOf(c)));
    }
    List<ClaimRecord> claims = new ArrayList<>();
    outcome
        .verdicts()
        .forEach(
            v ->
                claims.add(
                    new ClaimRecord(
                        v.findingId(),
                        v.sectionKey(),
                        v.attempt(),
                        v.status().name(),
                        v.reasonCodes(),
                        originOf.getOrDefault(v.findingId(), "LLM"))));
    long latency =
        job.completedAt() == null
            ? 0
            : Duration.between(job.createdAt(), job.completedAt()).toMillis();
    return b.findings(findings)
        .shown(new ArrayList<>(shown.values()))
        .claims(claims)
        .latencyMs(latency)
        .costMicros(outcome.costUsdMicros())
        .models(outcome.models())
        .promptVersions(outcome.promptVersions())
        .warnings(outcome.warnings().stream().map(AnalysisJob.Warning::code).toList())
        .build();
  }

  private static ShownCitation shownOf(VerifiedCitation c) {
    return new ShownCitation(c.citationId(), c.sectionKey(), c.verification().status().name());
  }

  private AnalysisCase baseline(
      String contractId, Side side, AnalysisJob job, List<CloudCapture.Call> calls, long ms) {
    Baseline result = job.baseline();
    var b =
        AnalysisCase.builder(contractId, "baseline", side.name())
            .piiMode(piiMode)
            .language(job.language())
            .latencyMs(ms)
            .costMicros(calls.stream().mapToLong(CloudCapture.Call::costMicros).sum());
    withCalls(b, calls);
    if (result == null || !"OK".equals(result.status())) {
      return b.outcome("FAILED")
          .failureCode(result == null ? "no-baseline" : result.reason())
          .build();
    }
    List<PredictedFinding> findings = new ArrayList<>();
    List<ClaimRecord> claims = new ArrayList<>();
    int i = 0;
    for (Baseline.Finding f : result.findings()) {
      String id = "b" + (++i);
      findings.add(
          new PredictedFinding(
              id, List.of(), "", "", f.severity(), "BASELINE", 0, List.of(), "BASELINE"));
      for (Baseline.Checked c : f.citations()) {
        claims.add(
            new ClaimRecord(
                id, c.claimed().sectionKey(), 1, c.status().name(), c.reasonCodes(), "BASELINE"));
      }
    }
    if (result.modelId() != null) {
      b.models(Map.of("BASELINE", result.modelId()));
    }
    return b.findings(findings).claims(claims).build();
  }

  /** Verifies one adversarial citation as an external claim (no candidate set, no document). */
  public CitationCase verify(AdversarialCase c) {
    CloudCapture.label("citations:" + c.id());
    int mark = CloudCapture.mark();
    ClaimedCitation claim =
        ClaimedCitation.of(
            c.sectionKey(),
            c.quote(),
            SensitiveText.of(c.claim()),
            c.claim().codePoints().anyMatch(cp -> cp >= 0x0980 && cp <= 0x09FF) ? "bn" : "en",
            CitationOrigin.EXTERNAL,
            null,
            null);
    String release = law.corpusRelease() == null ? "none" : law.corpusRelease();
    VerificationContext context =
        new VerificationContext(
            clock.instant().atZone(DHAKA).toLocalDate(),
            properties.analysis().jurisdictions(),
            release,
            Set.of(),
            1);
    Verdict verdict = verifier.verify(claim, context);
    var support = verdict.result().checks().support();
    boolean recorded =
        !CloudCapture.active()
            || CloudCapture.since(mark).stream().allMatch(CloudCapture.Call::replayed);
    return new CitationCase(
        c.id(),
        c.cls(),
        verdict.result().status().name(),
        verdict.result().reasonCodes().stream().map(ReasonCode::name).toList(),
        support.label() == null ? null : support.label().name(),
        recorded);
  }

  /** The corpus release the verifier reads. */
  public @Nullable String corpusRelease() {
    return law.corpusRelease();
  }
}
