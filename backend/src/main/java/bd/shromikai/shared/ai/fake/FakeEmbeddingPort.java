package bd.shromikai.shared.ai.fake;

import bd.shromikai.shared.ai.EmbeddingPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Returns stable unit vectors seeded from the SHA-256 of each input, so the same text always maps
 * to the same vector on every JVM. Tests can pin hand-set vectors for ranking cases.
 */
public final class FakeEmbeddingPort implements EmbeddingPort {

  public static final int DIMENSIONS = 1024;

  private final String modelId;
  private final Map<String, float[]> pinned = new ConcurrentHashMap<>();

  public FakeEmbeddingPort(String modelId) {
    this.modelId = modelId;
  }

  @Override
  public String modelId() {
    return modelId;
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }

  /** Uses {@code vector} (normalised to unit length) for {@code text}. */
  public void pin(String text, float[] vector) {
    if (vector.length != DIMENSIONS) {
      throw new IllegalArgumentException("vector must have " + DIMENSIONS + " dimensions");
    }
    pinned.put(text, normalise(vector.clone()));
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    return texts.stream().map(this::vectorFor).toList();
  }

  private float[] vectorFor(String text) {
    float[] vector = pinned.get(text);
    if (vector != null) {
      return vector.clone();
    }
    Random random = new Random(seed(text));
    float[] generated = new float[DIMENSIONS];
    for (int i = 0; i < DIMENSIONS; i++) {
      generated[i] = (float) random.nextGaussian();
    }
    return normalise(generated);
  }

  private static long seed(String text) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static float[] normalise(float[] vector) {
    double sum = 0;
    for (float value : vector) {
      sum += (double) value * value;
    }
    double norm = Math.sqrt(sum);
    if (norm == 0) {
      throw new IllegalArgumentException("zero vector");
    }
    for (int i = 0; i < vector.length; i++) {
      vector[i] = (float) (vector[i] / norm);
    }
    return vector;
  }
}
