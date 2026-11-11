package bd.shromikai.api.internal;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@code Idempotency-Key} of {@code POST /analyses} (api.md §1): the same subject and key within 30
 * minutes returns the original analysis; the same key with a different request body is {@code
 * idempotency-key-reused} (409). Holds only the subject, the key, a body hash and the analysis ID,
 * in memory (R-02).
 */
@Component
public final class IdempotencyCache {

  private record Entry(String bodyHash, String analysisId) {}

  private final Cache<String, Entry> entries =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(30)).maximumSize(10_000).build();

  /** The ID of the original analysis, empty for a new key. */
  public Optional<String> lookup(String subjectKey, String key, String bodyHash) {
    Entry entry = entries.getIfPresent(subjectKey + "|" + key);
    if (entry == null) {
      return Optional.empty();
    }
    if (!entry.bodyHash().equals(bodyHash)) {
      throw new ProblemException(ProblemType.IDEMPOTENCY_KEY_REUSED);
    }
    return Optional.of(entry.analysisId());
  }

  public void remember(String subjectKey, String key, String bodyHash, String analysisId) {
    entries.put(subjectKey + "|" + key, new Entry(bodyHash, analysisId));
  }
}
