package bd.shromikai.api;

import bd.shromikai.auth.AccountView;
import bd.shromikai.auth.AuthResult;
import bd.shromikai.auth.AuthService;
import bd.shromikai.auth.AuthenticatedSubject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-03 to E-07 and E-10: login, refresh, logout, me, guest and password change (FR-05, FR-09).
 * Tokens travel only in the {@code Authorization} header; the refresh token only in an {@code
 * HttpOnly} cookie scoped to {@code /api/v1/auth}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  public static final String REFRESH_COOKIE = "shromik_refresh";
  static final String COOKIE_PATH = "/api/v1/auth";

  private final AuthService auth;
  private final boolean secureCookie;

  public AuthController(
      AuthService auth, bd.shromikai.shared.config.ShromikaiProperties properties) {
    this.auth = auth;
    this.secureCookie = properties.auth().cookieSecure();
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return withCookie(auth.login(request.email(), request.password(), http.getRemoteAddr()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String refreshToken,
      HttpServletRequest http) {
    return withCookie(auth.refresh(refreshToken, http.getRemoteAddr()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal AuthenticatedSubject subject,
      @CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String refreshToken) {
    auth.logout(subject, refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
        .build();
  }

  @GetMapping("/me")
  public Me me(@AuthenticationPrincipal AuthenticatedSubject subject) {
    AccountView view = auth.me(subject);
    return Me.from(view);
  }

  /** Body of {@code PATCH /auth/me}; values are validated in {@code AuthService} with pointers. */
  @io.swagger.v3.oas.annotations.media.Schema(name = "MeUpdate")
  public record MeUpdate(@Nullable String locale, @Nullable Map<String, Object> profile) {}

  @org.springframework.web.bind.annotation.PatchMapping("/me")
  public Me updateMe(
      @AuthenticationPrincipal AuthenticatedSubject subject, @RequestBody MeUpdate update) {
    return Me.from(auth.updateProfile(subject, update.locale(), update.profile()));
  }

  @PostMapping("/guest")
  public ResponseEntity<TokenResponse> guest(
      @Valid @RequestBody GuestRequest request, HttpServletRequest http) {
    return withCookie(auth.guest(request.deviceToken(), http.getRemoteAddr()));
  }

  @PostMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthenticatedSubject subject,
      @Valid @RequestBody PasswordChangeRequest request) {
    auth.changePassword(subject, request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  /** Token response plus the rotated refresh cookie (none for guests). */
  ResponseEntity<TokenResponse> withCookie(AuthResult result) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (result.refreshToken() != null) {
      builder.header(
          HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.refreshTtl()).toString());
    }
    return builder.body(TokenResponse.from(result));
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }
}
