package bd.shromikai.auth;

import bd.shromikai.auth.internal.ApiKeyGenerator;
import bd.shromikai.auth.internal.ApiKeyRepository;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Organisation API keys (FR-65, ADR-0021): created and revoked by an admin, shown once, stored as
 * SHA-256. Authentication looks the hash up; a revoked key never authenticates.
 */
@Service
public class ApiKeyService {

  /** The scopes a key can carry. */
  public static final List<String> SCOPES = List.of("analyses", "laws", "mcp", "automation");

  /** A new key: the view and the secret, which exists only in this object. */
  public record Created(ApiKeyView view, String key) {
    @Override
    public String toString() {
      return "Created[" + view.id() + ", key masked]";
    }
  }

  private final ApiKeyRepository keys;

  ApiKeyService(ApiKeyRepository keys) {
    this.keys = keys;
  }

  public Created create(UUID orgId, String name, List<String> scopes, @Nullable UUID createdBy) {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty() || trimmed.length() > 60) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "name must be 1 to 60 characters");
    }
    Set<String> unique = new LinkedHashSet<>(scopes == null ? List.of() : scopes);
    if (unique.isEmpty() || !SCOPES.containsAll(unique)) {
      throw new ProblemException(
          ProblemType.VALIDATION_FAILED, "scopes must be a non-empty subset of " + SCOPES);
    }
    String key = ApiKeyGenerator.generate();
    ApiKeyView view =
        keys.insert(
            orgId,
            ApiKeyGenerator.prefix(key),
            ApiKeyGenerator.hash(key),
            trimmed,
            List.copyOf(unique),
            createdBy);
    return new Created(view, key);
  }

  public List<ApiKeyView> list(UUID orgId) {
    return keys.list(orgId);
  }

  /** Revokes a key of this organisation; {@code not-found} for another organisation's key. */
  public void revoke(UUID orgId, UUID id) {
    if (!keys.revoke(orgId, id)) {
      throw new ProblemException(ProblemType.NOT_FOUND);
    }
  }

  /** The principal of an active key, or empty for an unknown, malformed or revoked one. */
  public Optional<ApiKeyPrincipal> authenticate(String rawKey) {
    if (!ApiKeyGenerator.looksLikeKey(rawKey)) {
      return Optional.empty();
    }
    Optional<ApiKeyPrincipal> principal = keys.activeByHash(ApiKeyGenerator.hash(rawKey));
    principal.ifPresent(p -> keys.touch(p.keyId()));
    return principal;
  }
}
