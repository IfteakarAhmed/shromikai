package bd.shromikai.api;

import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisPipeline;
import bd.shromikai.analysis.AnalysisRequest;
import bd.shromikai.analysis.AnalysisService;
import bd.shromikai.analysis.AnalysisSubject;
import bd.shromikai.analysis.Clause;
import bd.shromikai.analysis.Finding;
import bd.shromikai.analysis.JobStore;
import bd.shromikai.analysis.Redline;
import bd.shromikai.analysis.Side;
import bd.shromikai.analysis.TraceEvent;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.verify.VerifiedCitation;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Debug endpoints of the {@code dev} profile only (E-45, FR-33): start the P5 pipeline (intake,
 * redaction, segmentation, classification), optionally continue it through the P6 pipeline ({@code
 * full=true}), and look at the redacted clauses, the findings with their verified citations and the
 * content-free trace. Outside {@code dev} this bean does not exist, so every route is {@code 404}.
 * Admin only. The public analysis API arrives in P7; findings here are redacted, not re-identified.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev/analyses")
public class DevAnalysisController {

  private final AnalysisService analyses;
  private final AnalysisPipeline pipeline;
  private final JobStore jobs;

  public DevAnalysisController(AnalysisService analyses, AnalysisPipeline pipeline, JobStore jobs) {
    this.analyses = analyses;
    this.pipeline = pipeline;
    this.jobs = jobs;
  }

  /** A finding as the API shows it (api.md §5.8), without re-identification. */
  public record FindingView(
      String id,
      String title,
      String kind,
      String severity,
      boolean nonObvious,
      String source,
      String clauseType,
      List<String> clauseIds,
      String explanation,
      String whyItMatters,
      String verificationStatus,
      List<VerifiedCitation> citations,
      String ruleId,
      String redlineId,
      String reviewItemId,
      double confidence) {}

  public record RedlineView(
      String id,
      String findingId,
      String clauseId,
      String action,
      String originalText,
      String proposedText,
      String rationale,
      List<String> ruleIds,
      int attempts) {}

  public record WarningView(String code, String message) {}

  public record Findings(
      String status,
      List<FindingView> findings,
      List<RedlineView> redlines,
      List<WarningView> warnings,
      int reviewItems,
      String verifierIndependence,
      List<TraceView> trace) {}

  /** Text of 200 to 60,000 characters. */
  public record TextRequest(@NotNull @Size(min = 200, max = 60_000) String text) {}

  public record Started(@JsonProperty("analysisId") UUID analysisId, String status, String stage) {}

  public record ClauseView(
      String id,
      String label,
      String type,
      double confidence,
      String redactedText,
      List<String> flags) {}

  public record TraceView(
      String stage,
      long durationMs,
      String outcome,
      String promptId,
      Integer promptVersion,
      String modelId,
      Map<String, Object> detail) {}

  public record Redacted(List<ClauseView> clauses, List<TraceView> trace) {}

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Started upload(
      @RequestParam("file") List<MultipartFile> files,
      @RequestParam(name = "full", defaultValue = "false") boolean full,
      @RequestParam(name = "side", defaultValue = "WORKER") Side side)
      throws IOException {
    List<byte[]> bytes = new ArrayList<>();
    for (MultipartFile file : files) {
      bytes.add(file.getBytes());
    }
    return started(analyses.analyseFiles(bytes), full, side);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Started text(
      @Valid @RequestBody TextRequest request,
      @RequestParam(name = "full", defaultValue = "false") boolean full,
      @RequestParam(name = "side", defaultValue = "WORKER") Side side) {
    return started(analyses.analyseText(request.text()), full, side);
  }

  @GetMapping("/{id}/findings")
  public Findings findings(@PathVariable UUID id) {
    AnalysisJob job = jobs.find(id).orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
    AnalysisJob.Outcome outcome = job.outcome();
    if (outcome == null) {
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
    List<FindingView> findings = new ArrayList<>();
    for (Finding f : outcome.findings()) {
      findings.add(
          new FindingView(
              f.id(),
              f.title().reveal(),
              f.kind().name(),
              f.severity().name(),
              f.nonObvious(),
              f.source().name(),
              f.clauseType(),
              f.clauseIds(),
              f.explanation().reveal(),
              f.whyItMatters().reveal(),
              f.verification().name(),
              f.citations(),
              f.ruleId(),
              f.redlineId(),
              f.reviewItemId() == null ? null : f.reviewItemId().toString(),
              f.confidence()));
    }
    List<RedlineView> redlines = new ArrayList<>();
    for (Redline r : outcome.redlines()) {
      redlines.add(
          new RedlineView(
              r.id(),
              r.findingId(),
              r.clauseId(),
              r.action(),
              r.originalText() == null ? null : r.originalText().reveal(),
              r.proposedText().reveal(),
              r.rationale().reveal(),
              r.ruleIds(),
              r.attempts()));
    }
    return new Findings(
        job.status().name(),
        findings,
        redlines,
        outcome.warnings().stream().map(w -> new WarningView(w.code(), w.message())).toList(),
        outcome.reviewItems(),
        outcome.verifierIndependence(),
        traceViews(job));
  }

  @GetMapping("/{id}/redacted")
  public Redacted redacted(@PathVariable UUID id) {
    AnalysisJob job = jobs.find(id).orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
    List<ClauseView> clauses = new ArrayList<>();
    for (Clause clause : job.clauses()) {
      clauses.add(
          new ClauseView(
              clause.id(),
              clause.label(),
              clause.type() == null ? null : clause.type().name(),
              clause.confidence(),
              clause.text().reveal(),
              clause.flags().stream().map(Enum::name).sorted().toList()));
    }
    return new Redacted(clauses, traceViews(job));
  }

  private static List<TraceView> traceViews(AnalysisJob job) {
    List<TraceView> trace = new ArrayList<>();
    for (TraceEvent e : job.trace()) {
      trace.add(
          new TraceView(
              e.stage().name(),
              e.durationMs(),
              e.outcome(),
              e.promptId(),
              e.promptVersion(),
              e.modelId(),
              e.detail()));
    }
    return trace;
  }

  private Started started(AnalysisJob job, boolean full, Side side) {
    ProblemException failure = job.failure();
    if (failure != null) {
      throw failure;
    }
    if (full) {
      pipeline.run(job, AnalysisRequest.of(side), AnalysisSubject.dev());
      failure = job.failure();
      if (failure != null) {
        throw failure;
      }
    }
    return new Started(job.id(), job.status().name(), job.stage().name());
  }
}
