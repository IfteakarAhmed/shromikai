package bd.shromikai.auth;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Bucket4j limits on auth endpoints, per IP and per user, per minute (FR-07). Keys are hashed, so
 * the map never holds an e-mail address or an address in clear. Idle buckets are dropped once the
 * map grows large.
 */
@Component
public class AuthRateLimiter {

  private static final int PURGE_ABOVE = 20_000;

  private final int perIp;
  private final int perUser;
  private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();

  AuthRateLimiter(ShromikaiProperties properties) {
    this.perIp = properties.auth().rateLimit().perIpPerMinute();
    this.perUser = properties.auth().rateLimit().perUserPerMinute();
  }

  /** Counts one request from {@code clientIp}, or throws {@code rate-limited}. */
  public void checkIp(String clientIp) {
    consume(ipBuckets, Hashing.sha256Hex(clientIp), perIp);
  }

  /** Counts one request for a user key such as a lower-cased e-mail address or a user ID. */
  public void checkUser(String userKey) {
    consume(userBuckets, Hashing.sha256Hex(userKey), perUser);
  }

  private static void consume(ConcurrentHashMap<String, Bucket> buckets, String key, int limit) {
    if (buckets.size() > PURGE_ABOVE) {
      buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= limit);
    }
    Bucket bucket =
        buckets.computeIfAbsent(
            key,
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(limit)
                            .refillIntervally(limit, Duration.ofMinutes(1))
                            .build())
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
      throw new ProblemException(ProblemType.RATE_LIMITED, null, (int) seconds);
    }
  }
}
