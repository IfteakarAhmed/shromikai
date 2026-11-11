package bd.shromikai.api;

import bd.shromikai.auth.ApiKeyService;
import bd.shromikai.auth.ApiKeyView;
import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/admin/api-keys} (ADMIN, audited; FR-65, T-12, T-26). An admin of an organisation
 * manages that organisation's keys only; an admin without an organisation names one with {@code
 * orgId}. The secret is in the creation response and nowhere else.
 */
@RestController
@RequestMapping("/api/v1/admin/api-keys")
public class AdminApiKeyController {

  private final ApiKeyService keys;
  private final AuditService audit;

  public AdminApiKeyController(ApiKeyService keys, AuditService audit) {
    this.keys = keys;
    this.audit = audit;
  }

  public record CreateRequest(
      @Nullable String name, @Nullable List<String> scopes, @Nullable UUID orgId) {}

  /** The created key with its secret, shown once. */
  public record CreatedKey(
      UUID id,
      UUID orgId,
      String prefix,
      String name,
      List<String> scopes,
      String key,
      String createdAt) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedKey create(
      @AuthenticationPrincipal AuthenticatedSubject admin, @RequestBody CreateRequest request) {
    UUID org = organisation(admin, request.orgId());
    ApiKeyService.Created created =
        keys.create(org, request.name(), request.scopes(), admin.userId());
    ApiKeyView view = created.view();
    audit.record(
        AuditEvent.of(admin.userId(), ActorKind.USER, AuditAction.API_KEY_CREATED, Map.of())
            .withTarget("api_key", view.id().toString()));
    return new CreatedKey(
        view.id(),
        view.orgId(),
        view.prefix(),
        view.name(),
        view.scopes(),
        created.key(),
        view.createdAt().toString());
  }

  @GetMapping
  public Map<String, Object> list(
      @AuthenticationPrincipal AuthenticatedSubject admin,
      @RequestParam(required = false) @Nullable UUID orgId) {
    return Map.of("items", keys.list(organisation(admin, orgId)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(
      @AuthenticationPrincipal AuthenticatedSubject admin,
      @PathVariable UUID id,
      @RequestParam(required = false) @Nullable UUID orgId) {
    keys.revoke(organisation(admin, orgId), id);
    audit.record(
        AuditEvent.of(admin.userId(), ActorKind.USER, AuditAction.API_KEY_REVOKED, Map.of())
            .withTarget("api_key", id.toString()));
  }

  /** An admin of an organisation gets only its own; naming another one is {@code not-found}. */
  private static UUID organisation(AuthenticatedSubject admin, @Nullable UUID requested) {
    if (admin.orgId() != null) {
      if (requested != null && !requested.equals(admin.orgId())) {
        throw new ProblemException(ProblemType.NOT_FOUND);
      }
      return admin.orgId();
    }
    if (requested == null) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "orgId is required");
    }
    return requested;
  }
}
