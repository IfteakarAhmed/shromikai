package bd.shromikai.api;

import bd.shromikai.auth.AccountView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Response of {@code GET /api/v1/auth/me} (api.md §3, schema {@code Me}). A guest has no user ID,
 * e-mail address or organisation.
 */
@Schema(name = "Me")
public record Me(
    @Nullable UUID userId,
    @Nullable String email,
    String role,
    @Nullable Org org,
    String locale,
    @Nullable Map<String, Object> profile,
    boolean demo) {

  /** The organisation of an account. */
  @Schema(name = "MeOrg")
  public record Org(UUID id, String name, String kind, String plan) {}

  static Me from(AccountView view) {
    return new Me(
        view.userId(),
        view.email(),
        view.role().name(),
        view.org() == null
            ? null
            : new Org(view.org().id(), view.org().name(), view.org().kind(), view.org().plan()),
        view.locale(),
        view.profile(),
        view.demo());
  }
}
