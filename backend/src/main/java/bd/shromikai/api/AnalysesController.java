package bd.shromikai.api;

import bd.shromikai.analysis.AnalysisEvents;
import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisProperties;
import bd.shromikai.analysis.AnalysisRequest;
import bd.shromikai.analysis.AnalysisResults;
import bd.shromikai.analysis.AnalysisRunner;
import bd.shromikai.analysis.AnalysisSubject;
import bd.shromikai.analysis.Side;
import bd.shromikai.api.internal.AnalysisSubjects;
import bd.shromikai.api.internal.IdempotencyCache;
import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.auth.GuestQuotaService;
import bd.shromikai.auth.Role;
import bd.shromikai.rules.Playbook;
import bd.shromikai.rules.PlaybookStore;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * E-12 to E-17 {@code /api/v1/analyses} (FR-24, FR-54, FR-58). JSON text up to 8,000 characters is
 * answered {@code 200} when the analysis finishes within the wait ({@code sync-wait}, 45 s;
 * ADR-0020); everything else is {@code 202} with a job ID and Server-Sent Events. Every resource
 * belongs to the subject that created it; anyone else gets {@code 404} (T-07). Names are restored
 * only in {@code AnalysisService.resultFor}'s response; this controller never touches {@code
 * privacy}.
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysesController {

  /** JSON text up to this many characters may be answered synchronously (FR-58). */
  static final int SYNC_TEXT_LIMIT = 8_000;

  private final bd.shromikai.analysis.AnalysisService analyses;
  private final AnalysisRunner runner;
  private final AnalysisResults results;
  private final AnalysisEvents events;
  private final PlaybookStore playbooks;
  private final GuestQuotaService quotas;
  private final IdempotencyCache idempotency;
  private final AnalysisProperties properties;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final ExecutorService streams = Executors.newVirtualThreadPerTaskExecutor();

  public AnalysesController(
      bd.shromikai.analysis.AnalysisService analyses,
      AnalysisRunner runner,
      AnalysisResults results,
      AnalysisEvents events,
      PlaybookStore playbooks,
      GuestQuotaService quotas,
      IdempotencyCache idempotency,
      AnalysisProperties properties,
      ObjectMapper mapper,
      Clock clock) {
    this.analyses = analyses;
    this.runner = runner;
    this.results = results;
    this.events = events;
    this.playbooks = playbooks;
    this.quotas = quotas;
    this.idempotency = idempotency;
    this.properties = properties;
    this.mapper = mapper;
    this.clock = clock;
  }

  /** The reader's profile; it changes wording only (FR-56). */
  @Schema(name = "AnalysisProfile")
  public record Profile(
      @Nullable String sector,
      @Nullable @Size(max = 60) String jobRole,
      @Nullable String preferredLanguage,
      @Nullable String readingLevel) {}

  /** {@code AnalysisOptions} of api.md §4.1; also the body of a JSON text request. */
  @Schema(name = "AnalysisOptions")
  public record Options(
      @NotNull Side side,
      @NotNull String outputLanguage,
      @Nullable @Valid Profile profile,
      @Nullable UUID playbookId,
      @Nullable @Size(max = 40) String workerCategory,
      @Nullable String clientKind) {}

  @Schema(name = "AnalysisTextRequest")
  public record TextRequest(
      @NotNull @Size(min = 200, max = 60_000) String text,
      @NotNull Side side,
      @NotNull String outputLanguage,
      @Nullable @Valid Profile profile,
      @Nullable UUID playbookId,
      @Nullable @Size(max = 40) String workerCategory) {}

  @Schema(name = "AnalysisAccepted")
  public record Accepted(
      UUID analysisId, String status, String eventsUrl, String resultUrl, String deadlineAt) {}

  @Schema(name = "AnalysisStatusView")
  public record StatusView(
      UUID analysisId,
      String status,
      @Nullable String stage,
      int progress,
      @Nullable Object problem) {}

  // ---------------------------------------------------------------------------- start

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> startText(
      @AuthenticationPrincipal AuthenticatedSubject caller,
      @Valid @RequestBody TextRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
      HttpServletRequest http) {
    AnalysisSubject subject = AnalysisSubjects.of(caller, "API");
    String bodyHash =
        Hashing.sha256Hex(
            body.text()
                + "|"
                + body.side()
                + "|"
                + body.outputLanguage()
                + "|"
                + body.playbookId()
                + "|"
                + body.profile());
    Optional<ResponseEntity<Object>> replay = replay(subject, idempotencyKey, bodyHash);
    if (replay.isPresent()) {
      return replay.get();
    }
    AnalysisRequest request =
        request(caller, body.side(), body.outputLanguage(), body.profile(), body.playbookId());
    AnalysisJob job =
        begin(caller, http, release -> runner.startText(subject, request, body.text(), release));
    remember(subject, idempotencyKey, bodyHash, job);
    if (body.text().length() <= SYNC_TEXT_LIMIT && runner.awaitFinished(job)) {
      return ResponseEntity.ok(finished(job, subject));
    }
    return accepted(job);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Object> startMultipart(
      @AuthenticationPrincipal AuthenticatedSubject caller,
      @Valid @RequestPart("options") Options options,
      @RequestPart(name = "file", required = false) @Nullable MultipartFile file,
      @RequestPart(name = "images", required = false) @Nullable List<MultipartFile> images,
      @RequestPart(name = "text", required = false) @Nullable String text,
      @RequestHeader(value = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
      HttpServletRequest http)
      throws IOException {
    int parts =
        (file != null ? 1 : 0)
            + (images != null && !images.isEmpty() ? 1 : 0)
            + (text != null ? 1 : 0);
    if (parts != 1) {
      throw new ProblemException(
          ProblemType.VALIDATION_FAILED, "Send exactly one of file, images or text");
    }
    String clientKind = options.clientKind() == null ? "DESKTOP_WEB" : options.clientKind();
    AnalysisSubject subject = AnalysisSubjects.of(caller, clientKind);
    List<byte[]> files = new ArrayList<>();
    if (file != null) {
      files.add(file.getBytes());
    } else if (images != null && !images.isEmpty()) {
      for (MultipartFile image : images) {
        files.add(image.getBytes());
      }
    }
    String bodyHash =
        Hashing.sha256Hex(
            (text != null ? text : "")
                + files.stream().mapToInt(b -> b.length).sum()
                + "|"
                + options);
    Optional<ResponseEntity<Object>> replay = replay(subject, idempotencyKey, bodyHash);
    if (replay.isPresent()) {
      return replay.get();
    }
    AnalysisRequest request =
        request(
            caller,
            options.side(),
            options.outputLanguage(),
            options.profile(),
            options.playbookId());
    AnalysisJob job =
        begin(
            caller,
            http,
            release ->
                text != null
                    ? runner.startText(subject, request, text, release)
                    : runner.startFiles(subject, request, files, release));
    remember(subject, idempotencyKey, bodyHash, job);
    return accepted(job);
  }

  // ---------------------------------------------------------------------------- read

  @GetMapping("/{id}")
  public ResponseEntity<Object> get(
      @AuthenticationPrincipal AuthenticatedSubject caller, @PathVariable UUID id) {
    AnalysisSubject subject = AnalysisSubjects.of(caller, "API");
    AnalysisJob job = results.jobFor(id, subject);
    return switch (job.status()) {
      case ANALYSED -> ResponseEntity.ok(results.resultFor(id, subject));
      case FAILED ->
          ResponseEntity.ok(
              new StatusView(id, "FAILED", job.stage().name(), 100, problem(job.failure())));
      default ->
          ResponseEntity.status(HttpStatus.ACCEPTED)
              .body(new StatusView(id, "RUNNING", job.stage().name(), events.progress(job), null));
    };
  }

  @GetMapping("/{id}/obligations")
  public ObjectNode obligations(
      @AuthenticationPrincipal AuthenticatedSubject caller, @PathVariable UUID id) {
    AnalysisSubject subject = AnalysisSubjects.of(caller, "API");
    AnalysisJob job = results.jobFor(id, subject);
    if (job.status() == AnalysisJob.Status.FAILED && job.failure() != null) {
      throw job.failure();
    }
    if (job.status() != AnalysisJob.Status.ANALYSED) {
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
    return results.obligationsFor(id, subject);
  }

  @GetMapping("/{id}/trace")
  public ObjectNode trace(
      @AuthenticationPrincipal AuthenticatedSubject caller, @PathVariable UUID id) {
    return results.traceFor(id, AnalysisSubjects.of(caller, "API"));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AuthenticatedSubject caller, @PathVariable UUID id) {
    results.delete(id, AnalysisSubjects.of(caller, "API"));
  }

  // ---------------------------------------------------------------------------- events

  @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @AuthenticationPrincipal AuthenticatedSubject caller,
      @PathVariable UUID id,
      @RequestHeader(value = "Last-Event-ID", required = false) @Nullable String lastEventId,
      @RequestHeader(value = "Accept-Language", required = false) @Nullable String language) {
    AnalysisJob job = results.jobFor(id, AnalysisSubjects.of(caller, "API"));
    int after = lastEventId == null ? 0 : parseId(lastEventId);
    boolean english = language != null && language.toLowerCase().startsWith("en");
    SseEmitter emitter =
        new SseEmitter(properties.analysis().hardTimeout().plusSeconds(30).toMillis());
    streams.execute(() -> stream(emitter, job, after, english));
    return emitter;
  }

  private void stream(SseEmitter emitter, AnalysisJob job, int after, boolean english) {
    int sent = after;
    long lastBeat = clock.millis();
    try {
      while (true) {
        for (AnalysisEvents.Event event : events.of(job, english)) {
          if (event.id() > sent) {
            emitter.send(
                SseEmitter.event()
                    .id(String.valueOf(event.id()))
                    .name(event.name())
                    .data(mapper.writeValueAsString(event.data()), MediaType.TEXT_PLAIN));
            sent = event.id();
            lastBeat = clock.millis();
            if (event.last()) {
              emitter.complete();
              return;
            }
          }
        }
        if (clock.millis() - lastBeat >= 15_000) {
          emitter.send(SseEmitter.event().comment("keep-alive"));
          lastBeat = clock.millis();
        }
        Thread.sleep(100);
      }
    } catch (IOException | IllegalStateException e) {
      emitter.complete(); // the client went away
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      emitter.complete();
    }
  }

  private static int parseId(String value) {
    try {
      return Math.max(0, Integer.parseInt(value.trim()));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // ---------------------------------------------------------------------------- helpers

  private ObjectNode finished(AnalysisJob job, AnalysisSubject subject) {
    if (job.status() == AnalysisJob.Status.FAILED && job.failure() != null) {
      throw job.failure();
    }
    return results.resultFor(job.id(), subject);
  }

  private ResponseEntity<Object> accepted(AnalysisJob job) {
    String base = "/api/v1/analyses/" + job.id();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            new Accepted(
                job.id(),
                "QUEUED",
                base + "/events",
                base,
                job.createdAt().plus(properties.analysis().hardTimeout()).toString()));
  }

  private Optional<ResponseEntity<Object>> replay(
      AnalysisSubject subject, @Nullable String key, String bodyHash) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    if (key.length() > 64 || !key.matches("[\\x21-\\x7e]+")) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "Idempotency-Key is not valid");
    }
    return idempotency
        .lookup(subject.key(), key, bodyHash)
        .map(
            id -> {
              AnalysisJob job = results.jobFor(UUID.fromString(id), subject);
              return job.status() == AnalysisJob.Status.ANALYSED
                  ? ResponseEntity.<Object>ok(results.resultFor(job.id(), subject))
                  : accepted(job);
            });
  }

  private void remember(
      AnalysisSubject subject, @Nullable String key, String bodyHash, AnalysisJob job) {
    if (key != null && !key.isBlank()) {
      idempotency.remember(subject.key(), key, bodyHash, job.id().toString());
    }
  }

  /** Reserves a guest slot, starts the job and releases the slot when the job ends. */
  private AnalysisJob begin(
      AuthenticatedSubject caller,
      HttpServletRequest http,
      java.util.function.Function<Runnable, AnalysisJob> start) {
    GuestQuotaService.Reservation reservation = quotas.reserve(caller, http.getRemoteAddr());
    try {
      return start.apply(reservation::close);
    } catch (RuntimeException e) {
      reservation.close();
      throw e;
    }
  }

  private AnalysisRequest request(
      AuthenticatedSubject caller,
      Side side,
      String outputLanguage,
      @Nullable Profile profile,
      @Nullable UUID playbookId) {
    if (!"bn".equals(outputLanguage) && !"en".equals(outputLanguage)) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "outputLanguage must be bn or en");
    }
    Map<String, String> map = new LinkedHashMap<>();
    if (profile != null) {
      putIfPresent(map, "sector", profile.sector());
      putIfPresent(map, "job_role", profile.jobRole());
      putIfPresent(map, "preferred_language", profile.preferredLanguage());
      putIfPresent(map, "reading_level", profile.readingLevel());
    }
    return new AnalysisRequest(side, outputLanguage, map, playbook(caller, playbookId));
  }

  private @Nullable Playbook playbook(AuthenticatedSubject caller, @Nullable UUID playbookId) {
    if (playbookId == null) {
      return null;
    }
    if (caller.guest() || (caller.role() != Role.HR && caller.role() != Role.ADMIN)) {
      throw new ProblemException(ProblemType.FORBIDDEN);
    }
    return playbooks
        .find(playbookId)
        .filter(p -> caller.role() == Role.ADMIN || p.orgId().equals(caller.orgId()))
        .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }

  private static void putIfPresent(Map<String, String> map, String key, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      map.put(key, value);
    }
  }

  private static @Nullable Object problem(@Nullable ProblemException failure) {
    if (failure == null) {
      return null;
    }
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", failure.type().typeUri());
    problem.put("title", failure.type().title(java.util.Locale.of("bn")));
    problem.put("status", failure.type().status());
    problem.put("code", failure.type().code());
    return problem;
  }
}
