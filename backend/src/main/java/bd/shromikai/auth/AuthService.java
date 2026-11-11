package bd.shromikai.auth;

import bd.shromikai.auth.internal.RefreshTokenRepository;
import bd.shromikai.auth.internal.RefreshTokenRepository.StoredToken;
import bd.shromikai.auth.internal.UserRecord;
import bd.shromikai.auth.internal.UserRepository;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Login, refresh rotation, logout, guest and demo access, account lookup and password change (FR-05
 * to FR-10). Nothing here logs a token, a password or an e-mail address; audit rows carry reason
 * codes only.
 */
@Service
public class AuthService {

  private static final Pattern UUID_V4 =
      Pattern.compile(
          "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
          Pattern.CASE_INSENSITIVE);
  private static final String DEMO_DOMAIN = "demo.shromikai.app";

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final AuditService audit;
  private final AuthRateLimiter limiter;
  private final ShromikaiProperties.Auth settings;
  private final Clock clock;
  private final TransactionTemplate transactions;
  private final SecureRandom random = new SecureRandom();

  /**
   * Compared against when the e-mail is unknown, so timing does not reveal which accounts exist.
   */
  private final String timingHash;

  AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder encoder,
      JwtService jwt,
      AuditService audit,
      AuthRateLimiter limiter,
      ShromikaiProperties properties,
      Clock clock,
      TransactionTemplate transactions) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.encoder = encoder;
    this.jwt = jwt;
    this.audit = audit;
    this.limiter = limiter;
    this.settings = properties.auth();
    this.clock = clock;
    this.transactions = transactions;
    this.timingHash = encoder.encode("timing-equaliser-not-a-real-password");
  }

  public AuthResult login(String email, String password, String clientIp) {
    limiter.checkIp(clientIp);
    limiter.checkUser("login:" + email.toLowerCase(Locale.ROOT));
    Instant now = clock.instant();

    Optional<UserRecord> found = users.findByEmail(email);
    if (found.isEmpty()) {
      encoder.matches(password, timingHash);
      failure(null, "unknown_user", null);
      throw new ProblemException(ProblemType.INVALID_CREDENTIALS);
    }
    UserRecord user = found.get();
    if (user.disabledAt() != null) {
      encoder.matches(password, timingHash);
      failure(user.id(), "disabled", null);
      throw new ProblemException(ProblemType.INVALID_CREDENTIALS);
    }
    Instant lockedUntil = user.lockedUntil();
    if (lockedUntil != null) {
      if (lockedUntil.isAfter(now)) {
        failure(user.id(), "account_locked", null);
        long wait = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
        throw new ProblemException(ProblemType.ACCOUNT_LOCKED, null, (int) wait);
      }
      users.clearFailures(user.id());
    }
    if (!encoder.matches(password, user.passwordHash())) {
      int attempt =
          users.recordFailure(
              user.id(), settings.lockout().maxFailures(), now.plus(settings.lockout().duration()));
      failure(user.id(), "bad_password", attempt);
      throw new ProblemException(ProblemType.INVALID_CREDENTIALS);
    }
    users.clearFailures(user.id());
    audit.record(
        AuditEvent.of(
            user.id(),
            ActorKind.USER,
            AuditAction.LOGIN_SUCCESS,
            Map.of("role", user.role().name(), "demo", user.demo())));
    return issue(user, UUID.randomUUID());
  }

  public AuthResult refresh(@Nullable String rawToken, String clientIp) {
    limiter.checkIp(clientIp);
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
      throw new ProblemException(ProblemType.INVALID_TOKEN);
    }
    String hash = Hashing.sha256Hex(rawToken);
    Instant now = clock.instant();
    AuthResult result =
        transactions.execute(
            status -> {
              Optional<StoredToken> stored = refreshTokens.find(hash);
              if (stored.isEmpty() || !stored.get().expiresAt().isAfter(now)) {
                return null;
              }
              StoredToken token = stored.get();
              if (!refreshTokens.markRotated(token.id(), now)) {
                // A rotated token came back: assume it leaked and end the whole session family.
                refreshTokens.revokeFamily(token.familyId(), now);
                return null;
              }
              Optional<UserRecord> user = users.findById(token.userId());
              if (user.isEmpty() || user.get().disabledAt() != null) {
                refreshTokens.revokeFamily(token.familyId(), now);
                return null;
              }
              return issue(user.get(), token.familyId(), token.id());
            });
    if (result == null) {
      throw new ProblemException(ProblemType.INVALID_TOKEN);
    }
    return result;
  }

  /** Ends the session family of {@code rawToken} when it belongs to the caller. */
  public void logout(AuthenticatedSubject subject, @Nullable String rawToken) {
    if (subject.guest() || subject.userId() == null || rawToken == null || rawToken.isBlank()) {
      return;
    }
    refreshTokens
        .find(Hashing.sha256Hex(rawToken))
        .filter(token -> token.userId().equals(subject.userId()))
        .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
  }

  public AccountView me(AuthenticatedSubject subject) {
    if (subject.guest() || subject.userId() == null) {
      return AccountView.guest();
    }
    UserRecord user =
        users
            .findById(subject.userId())
            .orElseThrow(() -> new ProblemException(ProblemType.INVALID_TOKEN));
    return view(user);
  }

  private static final java.util.Set<String> SECTORS =
      java.util.Set.of(
          "READY_MADE_GARMENTS",
          "TEXTILES",
          "CONSTRUCTION",
          "IT_SOFTWARE",
          "RETAIL",
          "DOMESTIC_WORK",
          "SECURITY_SERVICES",
          "NGO",
          "BANKING_FINANCE",
          "OTHER");

  /**
   * Stores the locale and the personalisation profile of an account (E-09, FR-56). A guest cannot
   * (403); omitted values stay; the profile changes wording only and never findings.
   */
  public AccountView updateProfile(
      AuthenticatedSubject subject,
      @Nullable String locale,
      @Nullable Map<String, Object> profile) {
    if (subject.guest() || subject.userId() == null) {
      throw new ProblemException(ProblemType.FORBIDDEN);
    }
    java.util.List<bd.shromikai.shared.problem.ValidationProblemException.Error> errors =
        new java.util.ArrayList<>();
    if (locale != null && !locale.equals("bn") && !locale.equals("en")) {
      errors.add(error("/locale", "must be bn or en"));
    }
    if (profile != null) {
      validateProfile(profile, "/profile/", true, errors);
    }
    if (!errors.isEmpty()) {
      throw new bd.shromikai.shared.problem.ValidationProblemException(errors);
    }
    users.updateProfile(subject.userId(), locale, profile);
    return me(subject);
  }

  /**
   * Checks the fields of a personalisation profile; shared by the stored profile (E-09) and the
   * per-request profile of an analysis (FR-56) so both accept the same values. A {@code null} value
   * is a validation error for the stored profile ({@code nullIsError}) and is skipped for a request
   * that simply left the field out.
   */
  public static void validateProfile(
      Map<String, Object> profile,
      String pointerPrefix,
      boolean nullIsError,
      java.util.List<bd.shromikai.shared.problem.ValidationProblemException.Error> errors) {
    for (Map.Entry<String, Object> e : profile.entrySet()) {
      String key = e.getKey();
      Object value = e.getValue();
      String at = pointerPrefix + key;
      if (value == null && !nullIsError) {
        continue;
      }
      switch (key) {
        case "side" -> {
          if (!"WORKER".equals(value) && !"EMPLOYER".equals(value)) {
            errors.add(error(at, "must be WORKER or EMPLOYER"));
          }
        }
        case "sector" -> {
          if (!(value instanceof String v) || !SECTORS.contains(v)) {
            errors.add(error(at, "is not a sector"));
          }
        }
        case "jobRole" -> {
          if (value != null && (!(value instanceof String v) || v.length() > 60)) {
            errors.add(error(at, "must be at most 60 characters"));
          }
        }
        case "preferredLanguage" -> {
          if (!"bn".equals(value) && !"en".equals(value)) {
            errors.add(error(at, "must be bn or en"));
          }
        }
        case "readingLevel" -> {
          if (!"BASIC".equals(value) && !"STANDARD".equals(value)) {
            errors.add(error(at, "must be BASIC or STANDARD"));
          }
        }
        default -> errors.add(error(at, "is not a profile field"));
      }
    }
  }

  private static bd.shromikai.shared.problem.ValidationProblemException.Error error(
      String pointer, String message) {
    return new bd.shromikai.shared.problem.ValidationProblemException.Error(pointer, message);
  }

  public AuthResult guest(String deviceToken, String clientIp) {
    limiter.checkIp(clientIp);
    if (!UUID_V4.matcher(deviceToken).matches()) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "deviceToken must be a UUID v4");
    }
    JwtService.IssuedToken token =
        jwt.issueGuest(Hashing.sha256Hex(deviceToken.toLowerCase(Locale.ROOT)));
    return new AuthResult(token.value(), token.expiresInSeconds(), true, null, null, Duration.ZERO);
  }

  /** One-click login for a seeded demo account; the controller exists only in the demo profile. */
  public AuthResult demo(Role role, String clientIp) {
    limiter.checkIp(clientIp);
    UserRecord user =
        users
            .findByEmail(role.name().toLowerCase(Locale.ROOT) + "@" + DEMO_DOMAIN)
            .filter(UserRecord::demo)
            .orElseThrow(() -> new ProblemException(ProblemType.NOT_FOUND));
    audit.record(
        AuditEvent.of(
            user.id(),
            ActorKind.USER,
            AuditAction.DEMO_LOGIN,
            Map.of("role", user.role().name(), "demo", true)));
    return issue(user, UUID.randomUUID());
  }

  public void changePassword(
      AuthenticatedSubject subject, String currentPassword, String newPassword) {
    if (subject.guest() || subject.userId() == null) {
      throw new ProblemException(ProblemType.FORBIDDEN);
    }
    if (subject.demo()) {
      throw new ProblemException(ProblemType.DEMO_ACCOUNT_RESTRICTED);
    }
    limiter.checkUser("password:" + subject.userId());
    UserRecord user =
        users
            .findById(subject.userId())
            .orElseThrow(() -> new ProblemException(ProblemType.INVALID_TOKEN));
    if (user.demo()) {
      throw new ProblemException(ProblemType.DEMO_ACCOUNT_RESTRICTED);
    }
    if (!encoder.matches(currentPassword, user.passwordHash())) {
      throw new ProblemException(ProblemType.INVALID_CREDENTIALS);
    }
    users.updatePasswordHash(user.id(), encoder.encode(newPassword));
    refreshTokens.revokeAllForUser(user.id(), clock.instant());
  }

  private AuthResult issue(UserRecord user, UUID family) {
    return issue(user, family, null);
  }

  private AuthResult issue(UserRecord user, UUID family, @Nullable UUID replaces) {
    Instant now = clock.instant();
    JwtService.IssuedToken access =
        jwt.issueUser(user.id(), user.role(), user.orgId(), user.demo());
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    UUID id =
        refreshTokens.insert(
            user.id(), family, Hashing.sha256Hex(raw), now.plus(settings.refreshTokenTtl()));
    if (replaces != null) {
      refreshTokens.setReplacedBy(replaces, id);
    }
    return new AuthResult(
        access.value(),
        access.expiresInSeconds(),
        false,
        view(user),
        raw,
        settings.refreshTokenTtl());
  }

  private AccountView view(UserRecord user) {
    OrgView org = user.orgId() == null ? null : users.findOrganisation(user.orgId()).orElse(null);
    return new AccountView(
        user.id(),
        user.email(),
        user.role(),
        org,
        user.locale(),
        user.profile().isEmpty() ? null : user.profile(),
        user.demo());
  }

  private void failure(@Nullable UUID userId, String reason, @Nullable Integer attempt) {
    Map<String, Object> detail =
        attempt == null ? Map.of("reason", reason) : Map.of("reason", reason, "attempt", attempt);
    audit.record(AuditEvent.of(userId, ActorKind.USER, AuditAction.LOGIN_FAILURE, detail));
  }
}
