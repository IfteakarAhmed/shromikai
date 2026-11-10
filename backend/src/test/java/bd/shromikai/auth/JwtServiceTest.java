package bd.shromikai.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("FR-05")
class JwtServiceTest {

  private static final String SECRET =
      "unit-test-secret-0123456789-abcdefghijklmnop-0123456789-abcdefghijklmnop";
  private static final Instant START = Instant.parse("2026-11-11T08:00:00Z");

  private MutableClock clock;
  private JwtService jwt;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(START);
    jwt = service(SECRET, "shromikai");
  }

  private JwtService service(String secret, String issuer) {
    return new JwtService(secret, issuer, Duration.ofMinutes(15), Duration.ofHours(2), clock);
  }

  @Test
  void shouldRoundTripUserClaims_whenTokenIsFresh() {
    UUID user = UUID.randomUUID();
    UUID org = UUID.randomUUID();

    JwtService.IssuedToken token = jwt.issueUser(user, Role.HR, org, true);
    AuthenticatedSubject subject = jwt.parse(token.value());

    assertThat(token.expiresInSeconds()).isEqualTo(900);
    assertThat(subject.userId()).isEqualTo(user);
    assertThat(subject.role()).isEqualTo(Role.HR);
    assertThat(subject.orgId()).isEqualTo(org);
    assertThat(subject.demo()).isTrue();
    assertThat(subject.guest()).isFalse();
  }

  @Test
  void shouldIssueTwoHourGuestToken_withDeviceHashAndNoUser() {
    JwtService.IssuedToken token = jwt.issueGuest("a".repeat(64));
    AuthenticatedSubject subject = jwt.parse(token.value());

    assertThat(token.expiresInSeconds()).isEqualTo(7200);
    assertThat(subject.guest()).isTrue();
    assertThat(subject.role()).isEqualTo(Role.WORKER);
    assertThat(subject.userId()).isNull();
    assertThat(subject.deviceHash()).isEqualTo("a".repeat(64));
  }

  @Test
  void shouldRejectToken_whenExpired() {
    String token = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();

    clock.advance(Duration.ofMinutes(15).plusSeconds(1));

    assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldAcceptToken_justBeforeExpiry() {
    String token = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();

    clock.advance(Duration.ofMinutes(15).minusSeconds(2));

    assertThat(jwt.parse(token).role()).isEqualTo(Role.WORKER);
  }

  @Test
  void shouldRejectToken_whenSignatureIsTampered() {
    String token = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();
    String[] parts = token.split("\\.");
    char last = parts[2].charAt(parts[2].length() - 2);
    String forgedSignature =
        parts[2].substring(0, parts[2].length() - 2)
            + (last == 'A' ? 'B' : 'A')
            + parts[2].charAt(parts[2].length() - 1);

    assertThatThrownBy(() -> jwt.parse(parts[0] + "." + parts[1] + "." + forgedSignature))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRejectToken_whenPayloadIsEditedToAdmin() {
    String token = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();
    String[] parts = token.split("\\.");
    String payload =
        new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"WORKER\"", "\"ADMIN\"");
    String forged =
        parts[0]
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
            + "."
            + parts[2];

    assertThatThrownBy(() -> jwt.parse(forged)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRejectToken_whenIssuerIsWrong() {
    String foreign =
        service(SECRET, "someone-else")
            .issueUser(UUID.randomUUID(), Role.ADMIN, null, false)
            .value();

    assertThatThrownBy(() -> jwt.parse(foreign)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRejectToken_whenSignedWithAnotherSecret() {
    String foreign =
        service("another-secret-0123456789-abcdefghijklmnopqrstu", "shromikai")
            .issueUser(UUID.randomUUID(), Role.ADMIN, null, false)
            .value();

    assertThatThrownBy(() -> jwt.parse(foreign)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRejectUnsignedToken_whenAlgorithmIsNone() {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("shromikai")
            .subject(UUID.randomUUID().toString())
            .claim("role", "ADMIN")
            .expirationTime(Date.from(START.plus(Duration.ofHours(1))))
            .build();
    String unsigned = new PlainJWT(claims).serialize();

    assertThatThrownBy(() -> jwt.parse(unsigned)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRejectToken_whenSignedWithAnotherHmacAlgorithm() throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("shromikai")
            .subject(UUID.randomUUID().toString())
            .claim("role", "ADMIN")
            .expirationTime(Date.from(START.plus(Duration.ofHours(1))))
            .build();
    SignedJWT hs512 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
    hs512.sign(new MACSigner(SECRET.getBytes()));

    assertThatThrownBy(() -> jwt.parse(hs512.serialize()))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRejectGarbage() {
    assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
    assertThatThrownBy(() -> jwt.parse("")).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shouldRefuseShortSecret() {
    assertThatThrownBy(() -> service("too-short", "shromikai"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldNotLeakTokenOrSecret_inToStringOfIssuedToken() {
    JwtService.IssuedToken token = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false);

    assertThat(token.toString()).doesNotContain(token.value());
  }

  /** A clock that tests move forward. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
