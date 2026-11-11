package bd.shromikai.api;

import bd.shromikai.analysis.AnalysisResults;
import bd.shromikai.api.internal.AnalysisSubjects;
import bd.shromikai.auth.AuthenticatedSubject;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * E-18 to E-20 {@code /api/v1/compare} (FR-59): the same contract through a plain LLM (no
 * retrieval, no verifier) and through ShromikAI. Every baseline citation is checked by the verifier
 * and marked; only a {@code VERIFIED} one counts as verified (R-01). A compare counts as 2 analyses
 * for guest quotas. The compare ID is the ID of the analysis behind it, so its events are the
 * analysis events.
 */
@RestController
@RequestMapping("/api/v1/compare")
public class CompareController {

  private final AnalysesController analyses;
  private final AnalysisResults results;

  public CompareController(AnalysesController analyses, AnalysisResults results) {
    this.analyses = analyses;
    this.results = results;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> start(
      @AuthenticationPrincipal AuthenticatedSubject caller,
      @Valid @RequestBody AnalysesController.TextRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) @Nullable String key,
      HttpServletRequest http) {
    return analyses.doStartText(caller, body, key, http, true);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Object> get(
      @AuthenticationPrincipal AuthenticatedSubject caller, @PathVariable UUID id) {
    var result = results.compareFor(id, AnalysisSubjects.of(caller, "API"));
    if (result.isPresent()) {
      return ResponseEntity.ok(result.get());
    }
    ObjectNode running = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    running.put("compareId", id.toString());
    running.put("status", "RUNNING");
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(running);
  }

  @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @AuthenticationPrincipal AuthenticatedSubject caller,
      @PathVariable UUID id,
      @RequestHeader(value = "Last-Event-ID", required = false) @Nullable String lastEventId,
      @RequestHeader(value = "Accept-Language", required = false) @Nullable String language) {
    return analyses.events(caller, id, lastEventId, language);
  }
}
