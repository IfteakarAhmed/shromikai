package bd.shromikai.auth;

import bd.shromikai.shared.config.ShromikaiProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 access tokens (FR-05, FR-09; api.md §2). Only HS256 is accepted, the
 * issuer and expiry are always checked, and the secret must be at least 32 bytes. Tokens are never
 * logged: {@link IssuedToken#toString()} hides the value.
 */
@Service
public final class JwtService {

  private static final int MIN_SECRET_BYTES = 32;

  private final byte[] secret;
  private final String issuer;
  private final Duration accessTtl;
  private final Duration guestTtl;
  private final Clock clock;

  @Autowired
  JwtService(ShromikaiProperties properties, Clock clock) {
    this(
        properties.auth().jwtSecret(),
        properties.auth().issuer(),
        properties.auth().accessTokenTtl(),
        properties.auth().guestTokenTtl(),
        clock);
  }

  public JwtService(
      String secret, String issuer, Duration accessTtl, Duration guestTtl, Clock clock) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    if (this.secret.length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException("The JWT secret must be at least 32 bytes");
    }
    this.issuer = issuer;
    this.accessTtl = accessTtl;
    this.guestTtl = guestTtl;
    this.clock = clock;
  }

  /** A signed token and its lifetime; {@code toString()} never shows the token. */
  public record IssuedToken(String value, long expiresInSeconds) {
    @Override
    public String toString() {
      return "IssuedToken[expiresInSeconds=" + expiresInSeconds + "]";
    }
  }

  public IssuedToken issueUser(UUID userId, Role role, @Nullable UUID orgId, boolean demo) {
    JWTClaimsSet.Builder claims =
        base(userId.toString(), accessTtl)
            .claim("role", role.name())
            .claim("demo", demo)
            .claim("guest", false);
    if (orgId != null) {
      claims.claim("org", orgId.toString());
    }
    return sign(claims.build(), accessTtl);
  }

  public IssuedToken issueGuest(String deviceHash) {
    JWTClaimsSet claims =
        base("guest:" + deviceHash.substring(0, 16), guestTtl)
            .claim("role", Role.WORKER.name())
            .claim("demo", false)
            .claim("guest", true)
            .claim("dev", deviceHash)
            .build();
    return sign(claims, guestTtl);
  }

  /** The subject behind {@code token}, or {@link InvalidTokenException}. */
  public AuthenticatedSubject parse(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
          || !jwt.verify(new MACVerifier(secret))) {
        throw new InvalidTokenException("signature");
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      if (!issuer.equals(claims.getIssuer())) {
        throw new InvalidTokenException("issuer");
      }
      Date expiry = claims.getExpirationTime();
      if (expiry == null || !expiry.toInstant().isAfter(clock.instant())) {
        throw new InvalidTokenException("expired");
      }
      Role role = Role.valueOf(claims.getStringClaim("role"));
      boolean guest = Boolean.TRUE.equals(claims.getBooleanClaim("guest"));
      boolean demo = Boolean.TRUE.equals(claims.getBooleanClaim("demo"));
      if (guest) {
        String device = claims.getStringClaim("dev");
        if (device == null || !device.matches("[0-9a-f]{64}")) {
          throw new InvalidTokenException("device");
        }
        return AuthenticatedSubject.guest(device);
      }
      String org = claims.getStringClaim("org");
      return new AuthenticatedSubject(
          UUID.fromString(claims.getSubject()),
          role,
          org == null ? null : UUID.fromString(org),
          demo,
          false,
          null);
    } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
      throw new InvalidTokenException("malformed", e);
    }
  }

  private JWTClaimsSet.Builder base(String subject, Duration ttl) {
    Instant now = clock.instant();
    return new JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject(subject)
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(ttl)));
  }

  private IssuedToken sign(JWTClaimsSet claims, Duration ttl) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(secret));
      return new IssuedToken(jwt.serialize(), ttl.toSeconds());
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign an access token", e);
    }
  }
}
