package bd.shromikai.api;

import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisService;
import bd.shromikai.analysis.Clause;
import bd.shromikai.analysis.JobStore;
import bd.shromikai.analysis.TraceEvent;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
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
 * redaction, segmentation, classification) and look at the redacted clauses with their types, flags
 * and the content-free trace. Outside {@code dev} this bean does not exist, so every route is
 * {@code 404}. Admin only. The public analysis API arrives in P7.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev/analyses")
public class DevAnalysisController {

  private final AnalysisService analyses;
  private final JobStore jobs;

  public DevAnalysisController(AnalysisService analyses, JobStore jobs) {
    this.analyses = analyses;
    this.jobs = jobs;
  }

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
  public Started upload(@RequestParam("file") List<MultipartFile> files) throws IOException {
    List<byte[]> bytes = new ArrayList<>();
    for (MultipartFile file : files) {
      bytes.add(file.getBytes());
    }
    return started(analyses.analyseFiles(bytes));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Started text(@Valid @RequestBody TextRequest request) {
    return started(analyses.analyseText(request.text()));
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
    return new Redacted(clauses, trace);
  }

  private Started started(AnalysisJob job) {
    ProblemException failure = job.failure();
    if (failure != null) {
      throw failure;
    }
    return new Started(job.id(), job.status().name(), job.stage().name());
  }
}
