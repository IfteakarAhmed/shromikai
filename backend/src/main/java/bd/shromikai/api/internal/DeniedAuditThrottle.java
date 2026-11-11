package bd.shromikai.api.internal;

import bd.shromikai.shared.Hashing;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;

/**
 * Limits the audit rows written for refused API keys to a few per minute per source address, so a
 * flood of bad keys cannot fill {@code audit_event} (T-12). The request is refused either way; only
 * the row is skipped. Addresses are hashed and the map is bounded and expires, so it holds no
 * identifier in clear and cannot grow without limit.
 */
final class DeniedAuditThrottle {

  private final int perMinute;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().maximumSize(20_000).expireAfterAccess(Duration.ofMinutes(10)).build();

  DeniedAuditThrottle(int perMinute) {
    this.perMinute = perMinute;
  }

  /** Whether an audit row may be written now for this source address. */
  boolean tryAcquire(String address) {
    Bucket bucket =
        buckets.get(
            Hashing.sha256Hex(address == null ? "" : address),
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(perMinute)
                            .refillIntervally(perMinute, Duration.ofMinutes(1))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }
}
