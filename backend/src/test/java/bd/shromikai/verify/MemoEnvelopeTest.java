package bd.shromikai.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.SignedResultCodec.Envelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-55, T-08: one changed byte, a changed expiry or a wrong key is rejected. */
@Tag("unit")
@Requirement("FR-55")
class MemoEnvelopeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final byte[] KEY =
      "unit-test-envelope-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

  private static SignedResultCodec codec(Instant now, byte[] key) {
    return new SignedResultCodec(key, "k1", Duration.ofHours(24), Clock.fixed(now, ZoneOffset.UTC));
  }

  private static ObjectNode payload() throws Exception {
    return (ObjectNode)
        MAPPER.readTree(
            "{\"analysisId\":\"a1\",\"findings\":[{\"id\":\"f1\",\"title\":\"নোটিস\"}]}");
  }

  private static void assertRejected(Runnable open, ProblemType type) {
    assertThatThrownBy(open::run)
        .isInstanceOfSatisfying(ProblemException.class, p -> assertThat(p.type()).isEqualTo(type));
  }

  @Test
  void aSignedPayloadOpensAndTheSignatureIs43UrlSafeCharacters() throws Exception {
    Envelope envelope = codec(NOW, KEY).sign(payload());

    assertThat(envelope.signature()).matches("^[A-Za-z0-9_-]{43}$");
    assertThat(codec(NOW, KEY).open(envelope)).isEqualTo(payload());
    assertThat(envelope.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
  }

  @Test
  void oneChangedByteInTheTextIsSignatureInvalid() throws Exception {
    Envelope envelope = codec(NOW, KEY).sign(payload());
    ObjectNode changed = payload();
    ((ObjectNode) changed.path("findings").get(0)).put("title", "নোটিশ"); // one letter differs

    assertRejected(
        () ->
            codec(NOW, KEY)
                .open(
                    new Envelope(
                        changed,
                        envelope.keyId(),
                        envelope.algorithm(),
                        envelope.expiresAt(),
                        envelope.signature())),
        ProblemType.SIGNATURE_INVALID);
  }

  @Test
  void aLaterExpiryOrAnotherKeyIdIsSignatureInvalid() throws Exception {
    Envelope envelope = codec(NOW, KEY).sign(payload());

    assertRejected(
        () ->
            codec(NOW, KEY)
                .open(
                    new Envelope(
                        envelope.payload(),
                        envelope.keyId(),
                        envelope.algorithm(),
                        envelope.expiresAt().plusSeconds(1),
                        envelope.signature())),
        ProblemType.SIGNATURE_INVALID);
    assertRejected(
        () ->
            codec(NOW, KEY)
                .open(
                    new Envelope(
                        envelope.payload(),
                        "k2",
                        envelope.algorithm(),
                        envelope.expiresAt(),
                        envelope.signature())),
        ProblemType.SIGNATURE_INVALID);
  }

  @Test
  void anotherKeyCannotOpenIt() throws Exception {
    Envelope envelope = codec(NOW, KEY).sign(payload());
    byte[] other = "another-envelope-key-0123456789abcdefgh".getBytes(StandardCharsets.UTF_8);

    assertRejected(() -> codec(NOW, other).open(envelope), ProblemType.SIGNATURE_INVALID);
  }

  @Test
  void anExpiredEnvelopeIsRefusedWith410() throws Exception {
    Envelope envelope = codec(NOW, KEY).sign(payload());

    assertRejected(
        () -> codec(NOW.plus(Duration.ofHours(25)), KEY).open(envelope),
        ProblemType.ENVELOPE_EXPIRED);
  }

  @Test
  void aClientMayReorderKeysBeforeSendingItBack() throws Exception {
    Envelope envelope = codec(NOW, KEY).sign(payload());
    JsonNode reordered =
        MAPPER.readTree(
            "{\"findings\":[{\"title\":\"নোটিস\",\"id\":\"f1\"}],\"analysisId\":\"a1\"}");

    JsonNode opened =
        codec(NOW, KEY)
            .open(
                new Envelope(
                    reordered,
                    envelope.keyId(),
                    envelope.algorithm(),
                    envelope.expiresAt(),
                    envelope.signature()));

    assertThat(opened).isEqualTo(reordered);
  }
}
