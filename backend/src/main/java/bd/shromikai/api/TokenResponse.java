package bd.shromikai.api;

import bd.shromikai.auth.AuthResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Response of login, refresh, guest and demo calls (api.md §3, schema {@code TokenResponse}). */
@Schema(name = "TokenResponse")
public record TokenResponse(
    String accessToken, long expiresIn, String tokenType, boolean guest, @Nullable Me user) {

  static TokenResponse from(AuthResult result) {
    return new TokenResponse(
        result.accessToken(),
        result.expiresInSeconds(),
        "Bearer",
        result.guest(),
        result.user() == null ? null : Me.from(result.user()));
  }

  /** Masks the token so printing a response never leaks it. */
  @Override
  public String toString() {
    return "TokenResponse[guest=" + guest + ", accessToken=[MASKED]]";
  }
}
