package bd.shromikai.api;

import bd.shromikai.analysis.ClauseType;
import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.auth.Role;
import bd.shromikai.rules.Playbook;
import bd.shromikai.rules.PlaybookInput;
import bd.shromikai.rules.PlaybookStore;
import bd.shromikai.rules.PlaybookValidator;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.shared.problem.ValidationProblemException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-26 to E-30 {@code /api/v1/playbooks} (HR own organisation, ADMIN all; FR-57, NFR-12). A
 * playbook of another organisation is answered as {@code 404}, never {@code 403}, so its existence
 * is not revealed (T-26). Deleting archives.
 */
@RestController
@Validated
@RequestMapping("/api/v1/playbooks")
public class PlaybookController {

  private static final Set<String> CLAUSE_TYPES =
      Arrays.stream(ClauseType.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  private final PlaybookStore store;
  private final PlaybookValidator validator;

  public PlaybookController(PlaybookStore store, PlaybookValidator validator) {
    this.store = store;
    this.validator = validator;
  }

  @Schema(name = "PlaybookPage")
  public record PlaybookPage(List<Playbook> items, int page, int size, long totalItems) {}

  @GetMapping
  public PlaybookPage list(
      @AuthenticationPrincipal AuthenticatedSubject subject,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    UUID scope = scope(subject);
    List<Playbook> items = store.list(scope, page, size);
    return new PlaybookPage(items, page, size, store.count(scope));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Playbook create(
      @AuthenticationPrincipal AuthenticatedSubject subject,
      @RequestBody PlaybookInput input,
      @RequestParam(required = false) @Nullable UUID orgId) {
    check(input);
    UUID org = subject.role() == Role.ADMIN ? orgId : subject.orgId();
    if (org == null) {
      throw new ProblemException(
          subject.role() == Role.ADMIN ? ProblemType.VALIDATION_FAILED : ProblemType.FORBIDDEN);
    }
    return store.create(org, subject.userId(), input);
  }

  @GetMapping("/{id}")
  public Playbook get(
      @AuthenticationPrincipal AuthenticatedSubject subject, @PathVariable UUID id) {
    return visible(subject, id);
  }

  @PutMapping("/{id}")
  public Playbook replace(
      @AuthenticationPrincipal AuthenticatedSubject subject,
      @PathVariable UUID id,
      @RequestBody PlaybookInput input) {
    visible(subject, id);
    check(input);
    return store.replace(id, input).orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(
      @AuthenticationPrincipal AuthenticatedSubject subject, @PathVariable UUID id) {
    visible(subject, id);
    store.archive(id);
  }

  private void check(PlaybookInput input) {
    var errors = validator.validate(input, CLAUSE_TYPES);
    if (!errors.isEmpty()) {
      throw new ValidationProblemException(
          errors.stream()
              .map(e -> new ValidationProblemException.Error(e.pointer(), e.message()))
              .toList());
    }
  }

  /** The organisation an HR user is limited to; {@code null} (all) for an admin. */
  private static @Nullable UUID scope(AuthenticatedSubject subject) {
    if (subject.role() == Role.ADMIN) {
      return null;
    }
    // An HR account without an organisation sees nothing (an organisation that cannot exist).
    return subject.orgId() == null ? new UUID(0, 0) : subject.orgId();
  }

  private Playbook visible(AuthenticatedSubject subject, UUID id) {
    UUID scope = scope(subject);
    return store
        .find(id)
        .filter(p -> scope == null || p.orgId().equals(scope))
        .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
  }
}
