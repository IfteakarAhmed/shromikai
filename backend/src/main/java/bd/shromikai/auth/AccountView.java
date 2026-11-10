package bd.shromikai.auth;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What {@code GET /auth/me} returns. A guest has no user ID, e-mail address or organisation. */
public record AccountView(
    @Nullable UUID userId,
    @Nullable String email,
    Role role,
    @Nullable OrgView org,
    String locale,
    @Nullable Map<String, Object> profile,
    boolean demo) {

  static AccountView guest() {
    return new AccountView(null, null, Role.WORKER, null, "bn", null, false);
  }
}
