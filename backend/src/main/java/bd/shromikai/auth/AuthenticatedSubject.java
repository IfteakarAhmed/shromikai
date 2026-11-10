package bd.shromikai.auth;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The caller behind a validated access token. A guest has no user ID; {@code deviceHash} is the
 * SHA-256 of its device token and is the guest's stable subject (api.md §2).
 */
public record AuthenticatedSubject(
    @Nullable UUID userId,
    Role role,
    @Nullable UUID orgId,
    boolean demo,
    boolean guest,
    @Nullable String deviceHash) {

  public static AuthenticatedSubject guest(String deviceHash) {
    return new AuthenticatedSubject(null, Role.WORKER, null, false, true, deviceHash);
  }
}
