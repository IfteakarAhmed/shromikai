package bd.shromikai.api.internal;

import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-subject limit of law search requests per minute (api.md §8.1). Keys are hashed subject IDs,
 * so the map holds no query and no identifier in clear.
 */
@Component
public class SearchRateLimiter {

  private static final int PURGE_ABOVE = 20_000;

  private final int perMinute;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  SearchRateLimiter(ApiProperties properties) {
    this.perMinute = properties.lawSearchPerMinute();
  }

  /** Counts one search for the subject, or throws {@code rate-limited} with {@code Retry-After}. */
  public void check(AuthenticatedSubject subject) {
    if (buckets.size() > PURGE_ABOVE) {
      buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= perMinute);
    }
    String key =
        Hashing.sha256Hex(
            subject.userId() != null
                ? subject.userId().toString()
                : String.valueOf(subject.deviceHash()));
    Bucket bucket =
        buckets.computeIfAbsent(
            key,
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(perMinute)
                            .refillIntervally(perMinute, Duration.ofMinutes(1))
                            .build())
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
      throw new ProblemException(ProblemType.RATE_LIMITED, null, (int) seconds);
    }
  }
}
