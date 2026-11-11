package bd.shromikai.verify;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signed result envelope settings ({@code shromikai.memo.*}, architecture §7.7). {@code
 * envelopeKey} may be blank; the key is then derived from the JWT secret (see {@link
 * SignedResultCodec}), so a deployment needs no extra secret. {@code keyId} is written into every
 * envelope and memo footer.
 */
@ConfigurationProperties("shromikai.memo")
public record EnvelopeProperties(
    @Nullable String envelopeKey, @Nullable String keyId, @Nullable Duration envelopeTtl) {

  public EnvelopeProperties {
    if (keyId == null || keyId.isBlank()) {
      keyId = "k1";
    }
    if (envelopeTtl == null) {
      envelopeTtl = Duration.ofHours(24);
    }
  }
}
