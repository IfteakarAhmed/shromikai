package bd.shromikai.auth;

import java.util.Set;
import java.util.UUID;

/** The caller behind a valid API key: a machine client of one organisation (FR-65). */
public record ApiKeyPrincipal(UUID keyId, UUID orgId, String name, Set<String> scopes) {

  public ApiKeyPrincipal {
    scopes = Set.copyOf(scopes);
  }

  public boolean has(String scope) {
    return scopes.contains(scope);
  }
}
