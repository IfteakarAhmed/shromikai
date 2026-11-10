package bd.shromikai.auth.internal;

import bd.shromikai.auth.Role;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A row of {@code app_user}. {@code toString()} hides the hash and the e-mail address. */
public record UserRecord(
    UUID id,
    @Nullable UUID orgId,
    String email,
    String passwordHash,
    Role role,
    String locale,
    Map<String, Object> profile,
    boolean demo,
    int failedLogins,
    @Nullable Instant lockedUntil,
    @Nullable Instant disabledAt) {

  @Override
  public String toString() {
    return "UserRecord[id=" + id + ", role=" + role + "]";
  }
}
