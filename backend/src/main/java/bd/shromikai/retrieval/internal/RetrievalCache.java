package bd.shromikai.retrieval.internal;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.ai.EmbeddingPort;
import bd.shromikai.shared.text.BanglaNormalizer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Caffeine cache of query embeddings keyed by the SHA-256 of the normalised query (FR-21): the same
 * question asked twice calls {@link EmbeddingPort} once. Memory only; the keys may be user queries,
 * so the cache never reaches disk or a log (architecture §10.7).
 */
@Component
public class RetrievalCache {

  private final Cache<String, float[]> embeddings;
  private final EmbeddingPort embeddingPort;
  private final Counter hits;
  private final Counter misses;

  RetrievalCache(
      EmbeddingPort embeddingPort, RetrievalProperties properties, MeterRegistry registry) {
    this.embeddingPort = embeddingPort;
    this.embeddings =
        Caffeine.newBuilder()
            .maximumSize(properties.cache().maxEntries())
            .expireAfterWrite(properties.cache().ttl())
            .build();
    this.hits =
        Counter.builder("shromikai_retrieval_cache_total").tag("result", "hit").register(registry);
    this.misses =
        Counter.builder("shromikai_retrieval_cache_total").tag("result", "miss").register(registry);
  }

  /**
   * The cache key of a query: the SHA-256 of its {@link BanglaNormalizer#matchingForm matching
   * form}, so the cache never holds the query text itself.
   */
  public static String key(String query) {
    return Hashing.sha256Hex(BanglaNormalizer.matchingForm(query));
  }

  /** The embedding of the query, from the cache when the normalised query was seen recently. */
  public float[] embedding(String query) {
    String key = key(query);
    AtomicBoolean computed = new AtomicBoolean();
    float[] vector =
        embeddings.get(
            key,
            k -> {
              computed.set(true);
              List<float[]> result = embeddingPort.embed(List.of(query));
              return result.get(0);
            });
    (computed.get() ? misses : hits).increment();
    return vector.clone();
  }
}
