package bd.shromikai.analysis;

import bd.shromikai.ingest.InputKind;
import bd.shromikai.ingest.IntakeParagraph;
import bd.shromikai.ingest.IntakeResult;
import bd.shromikai.ingest.IntakeService;
import bd.shromikai.privacy.PrivacyShield;
import bd.shromikai.privacy.RedactedParagraph;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the first four pipeline steps of an analysis (P5): {@code INTAKE}, {@code OCR}, {@code
 * REDACT} and {@code CLASSIFY} (architecture §5.1). Redaction runs before the first cloud call; if
 * the local model fails, the job fails with {@code local-model-unavailable} and no cloud call has
 * been made (FR-31). Every step writes a content-free trace event; a failure is recorded on the
 * failing stage and the job's placeholder map is cleared.
 */
@Service
public class AnalysisService {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);

  private final IntakeService intake;
  private final PrivacyShield shield;
  private final ClauseSegmenter segmenter;
  private final ClauseClassifier classifier;
  private final JobStore jobs;
  private final Clock clock;

  public AnalysisService(
      IntakeService intake,
      PrivacyShield shield,
      ClauseSegmenter segmenter,
      ClauseClassifier classifier,
      JobStore jobs,
      Clock clock) {
    this.intake = intake;
    this.shield = shield;
    this.segmenter = segmenter;
    this.classifier = classifier;
    this.jobs = jobs;
    this.clock = clock;
  }

  /** Analyses uploaded files (one PDF, images, or a text file). */
  public AnalysisJob analyseFiles(List<byte[]> files) {
    return run(() -> intake.fromFiles(files));
  }

  /** Analyses pasted text. */
  public AnalysisJob analyseText(String text) {
    return run(() -> intake.fromText(text));
  }

  private AnalysisJob run(Supplier<IntakeResult> intakeStep) {
    AnalysisJob job = jobs.create();
    try {
      IntakeResult read = timed(job, Stage.INTAKE, intakeStep::get, r -> intakeDetail(r));
      job.intake(read.kind().name(), read.pageCount(), read.language());
      if (read.kind() == InputKind.PDF_SCAN || read.kind() == InputKind.IMAGE) {
        job.enter(Stage.OCR);
        job.record(
            event(
                Stage.OCR,
                0,
                TraceEvent.OK,
                null,
                Map.<String, Object>of(
                    "pages",
                    read.pages().stream().filter(p -> p.ocrConfidence() != null).count(),
                    "lowConfidencePages",
                    read.pages().stream()
                        .filter(p -> p.ocrConfidence() != null && p.ocrConfidence() < 80)
                        .count())));
      }
      job.enter(Stage.REDACT);
      List<String> originals = read.paragraphs().stream().map(p -> p.text().reveal()).toList();
      List<RedactedParagraph> redaction =
          timed(
              job,
              Stage.REDACT,
              () -> shield.redact(originals, job.placeholders()),
              r -> Map.of("placeholders", job.placeholders().size(), "paragraphs", r.size()));
      List<Para> paras = paragraphs(read, redaction);
      job.enter(Stage.CLASSIFY);
      long start = clock.millis();
      List<Clause> clauses = segmenter.split(paras, job.placeholders());
      ClauseClassifier.Result classified = classifier.classify(clauses, job.placeholders());
      job.record(
          new TraceEvent(
              Stage.CLASSIFY,
              clock.instant(),
              clock.millis() - start,
              TraceEvent.OK,
              classified.promptId(),
              classified.promptVersion(),
              classified.modelId(),
              classifyDetail(classified.clauses())));
      job.classified(classified.clauses());
    } catch (ProblemException problem) {
      failed(job, problem);
    } catch (RuntimeException unexpected) {
      LOG.error("analysis step failed unexpectedly: {}", unexpected.getClass().getSimpleName());
      failed(job, new ProblemException(ProblemType.INTERNAL_ERROR));
    }
    return job;
  }

  private List<Para> paragraphs(IntakeResult read, List<RedactedParagraph> redaction) {
    List<Para> out = new ArrayList<>();
    for (int i = 0; i < read.paragraphs().size(); i++) {
      IntakeParagraph source = read.paragraphs().get(i);
      RedactedParagraph redacted = redaction.get(i);
      Set<ClauseFlag> flags = EnumSet.noneOf(ClauseFlag.class);
      if (redacted.instructionLike()) {
        flags.add(ClauseFlag.INSTRUCTION_LIKE_TEXT);
      }
      if (source.lowOcrConfidence()) {
        flags.add(ClauseFlag.LOW_OCR_CONFIDENCE);
      }
      if (source.legacyFont()) {
        flags.add(ClauseFlag.LEGACY_FONT_CONVERTED);
      }
      out.add(new Para(source.id(), redacted.text(), flags));
    }
    return out;
  }

  private <T> T timed(
      AnalysisJob job,
      Stage stage,
      Supplier<T> step,
      java.util.function.Function<T, Map<String, Object>> detail) {
    job.enter(stage);
    long start = clock.millis();
    T result = step.get();
    job.record(event(stage, clock.millis() - start, TraceEvent.OK, null, detail.apply(result)));
    return result;
  }

  private static Map<String, Object> intakeDetail(IntakeResult r) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("inputKind", r.kind().name());
    detail.put("pages", r.pageCount());
    detail.put("paragraphs", r.paragraphs().size());
    detail.put("language", r.language());
    return detail;
  }

  private static Map<String, Object> classifyDetail(List<Clause> clauses) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("clauses", clauses.size());
    long flagged =
        clauses.stream().filter(c -> c.flags().contains(ClauseFlag.INSTRUCTION_LIKE_TEXT)).count();
    detail.put("flaggedClauses", flagged);
    if (flagged > 0) {
      detail.put("flags", List.of(ClauseFlag.INSTRUCTION_LIKE_TEXT.name()));
      detail.put(
          "flaggedClauseIds",
          clauses.stream()
              .filter(c -> c.flags().contains(ClauseFlag.INSTRUCTION_LIKE_TEXT))
              .map(Clause::id)
              .toList());
    }
    return detail;
  }

  private TraceEvent event(
      Stage stage,
      long durationMs,
      String outcome,
      @Nullable String reason,
      Map<String, Object> detail) {
    Map<String, Object> d = new LinkedHashMap<>(detail);
    if (reason != null) {
      d.put("reason", reason);
    }
    return new TraceEvent(stage, Instant.now(clock), durationMs, outcome, null, null, null, d);
  }

  private void failed(AnalysisJob job, ProblemException problem) {
    String reason =
        problem instanceof ModelException model ? model.reasonCode() : problem.type().code();
    job.record(
        event(
            job.stage(),
            0,
            TraceEvent.FAILED,
            reason.replaceAll("[^a-z0-9-]", "-"),
            Map.of("problem", problem.type().code())));
    job.fail(problem);
  }
}
