package bd.shromikai.retrieval.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.fake.FakeEmbeddingPort;
import bd.shromikai.testsupport.Requirement;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-21: a second identical query does not call the embedding port. */
@Tag("unit")
@Requirement("FR-21")
class RetrievalCacheTest {

  private final AtomicInteger calls = new AtomicInteger();
  private final FakeEmbeddingPort delegate = new FakeEmbeddingPort("bge-m3");
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private RetrievalCache cache() {
    var port =
        new bd.shromikai.shared.ai.EmbeddingPort() {
          @Override
          public String modelId() {
            return delegate.modelId();
          }

          @Override
          public int dimensions() {
            return delegate.dimensions();
          }

          @Override
          public List<float[]> embed(List<String> texts) {
            calls.incrementAndGet();
            return delegate.embed(texts);
          }
        };
    return new RetrievalCache(
        port,
        new RetrievalProperties(
            20,
            20,
            8,
            60,
            4,
            4,
            100,
            16,
            new RetrievalProperties.Cache(100, Duration.ofHours(1)),
            "x",
            false,
            1.0,
            false),
        registry);
  }

  @Test
  void shouldNotCallTheEmbeddingPortForTheSameQueryTwice() {
    RetrievalCache cache = cache();

    float[] first = cache.embedding("overtime pay rate");
    float[] second = cache.embedding("overtime pay rate");

    assertThat(calls).hasValue(1);
    assertThat(second).containsExactly(first);
    assertThat(registry.counter("shromikai_retrieval_cache_total", "result", "hit").count())
        .isEqualTo(1.0);
    assertThat(registry.counter("shromikai_retrieval_cache_total", "result", "miss").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldTreatQueriesThatNormaliseAlikeAsTheSameQuery() {
    RetrievalCache cache = cache();

    cache.embedding("Overtime   Pay 100");
    cache.embedding("overtime pay ১০০");

    assertThat(calls).hasValue(1);
  }

  @Test
  void shouldEmbedDifferentQueriesSeparately() {
    RetrievalCache cache = cache();

    cache.embedding("overtime");
    cache.embedding("leave");

    assertThat(calls).hasValue(2);
  }

  @Test
  void shouldHandOutCopiesSoCallersCannotCorruptTheCache() {
    RetrievalCache cache = cache();

    cache.embedding("overtime")[0] = 99f;

    assertThat(cache.embedding("overtime")[0]).isNotEqualTo(99f);
  }
}
