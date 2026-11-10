package bd.shromikai.auth;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Result of a login, refresh, guest or demo call. {@code refreshToken} is the raw token for the
 * cookie (null for guests). {@link #toString()} masks both tokens.
 */
public record AuthResult(
    String accessToken,
    long expiresInSeconds,
    boolean guest,
    @Nullable AccountView user,
    @Nullable String refreshToken,
    Duration refreshTtl) {

  @Override
  public String toString() {
    return "AuthResult[guest=" + guest + ", tokens=[MASKED]]";
  }
}
