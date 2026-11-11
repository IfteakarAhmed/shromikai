package bd.shromikai.verify;

import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.json.CanonicalJson;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Signs and opens the result envelope (FR-55, ADR-0006). The signature is HMAC-SHA256 over the RFC
 * 8785 canonical payload, the key ID and the expiry, so none of the three can be changed. It works
 * on JSON trees and never on the analysis types, so {@code verify} does not depend on {@code
 * analysis}. Comparison is constant-time; the server stores nothing about an envelope.
 */
@Component
public final class SignedResultCodec {

  public static final String ALGORITHM = "HMAC-SHA256";

  /** A signed payload as it travels to the client and back. */
  public record Envelope(
      JsonNode payload, String keyId, String algorithm, Instant expiresAt, String signature) {}

  private final byte[] key;
  private final String keyId;
  private final Duration ttl;
  private final Clock clock;

  @Autowired
  SignedResultCodec(EnvelopeProperties properties, ShromikaiProperties shared, Clock clock) {
    this(
        keyBytes(properties.envelopeKey(), shared.auth().jwtSecret()),
        properties.keyId(),
        properties.envelopeTtl(),
        clock);
  }

  public SignedResultCodec(byte[] key, String keyId, Duration ttl, Clock clock) {
    if (key.length < 32) {
      throw new IllegalArgumentException("The envelope key must be at least 32 bytes");
    }
    this.key = key.clone();
    this.keyId = keyId;
    this.ttl = ttl;
    this.clock = clock;
  }

  private static byte[] keyBytes(String configured, String jwtSecret) {
    if (configured != null && !configured.isBlank()) {
      return configured.getBytes(StandardCharsets.UTF_8);
    }
    return hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "shromikai-envelope-key-v1");
  }

  public String keyId() {
    return keyId;
  }

  public Envelope sign(JsonNode payload) {
    Instant expiresAt =
        clock.instant().plus(ttl).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    return new Envelope(payload, keyId, ALGORITHM, expiresAt, signature(payload, keyId, expiresAt));
  }

  /**
   * Returns the payload of an authentic, unexpired envelope.
   *
   * @throws ProblemException {@code signature-invalid} (400) or {@code envelope-expired} (410)
   */
  public JsonNode open(Envelope envelope) {
    if (!ALGORITHM.equals(envelope.algorithm()) || !keyId.equals(envelope.keyId())) {
      throw new ProblemException(ProblemType.SIGNATURE_INVALID);
    }
    byte[] expected =
        signature(envelope.payload(), envelope.keyId(), envelope.expiresAt())
            .getBytes(StandardCharsets.UTF_8);
    byte[] given = envelope.signature().getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, given)) {
      throw new ProblemException(ProblemType.SIGNATURE_INVALID);
    }
    if (!clock.instant().isBefore(envelope.expiresAt())) {
      throw new ProblemException(ProblemType.ENVELOPE_EXPIRED);
    }
    return envelope.payload();
  }

  private String signature(JsonNode payload, String id, Instant expiresAt) {
    String signed = CanonicalJson.of(payload) + "\n" + id + "\n" + expiresAt;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, signed));
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }
}
