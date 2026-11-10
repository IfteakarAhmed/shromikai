package bd.shromikai.shared.ai.ollama;

import bd.shromikai.shared.ai.EmbeddingPort;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import java.util.List;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

/** bge-m3 embeddings on Ollama through Spring AI ({@code /api/embed}, batched). */
public final class OllamaEmbeddingAdapter implements EmbeddingPort {

  public static final int BGE_M3_DIMENSIONS = 1024;

  private final EmbeddingModel embeddingModel;
  private final String modelId;

  public OllamaEmbeddingAdapter(EmbeddingModel embeddingModel, String modelId) {
    this.embeddingModel = embeddingModel;
    this.modelId = modelId;
  }

  @Override
  public String modelId() {
    return modelId;
  }

  @Override
  public int dimensions() {
    return BGE_M3_DIMENSIONS;
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }
    try {
      return embeddingModel
          .call(
              new EmbeddingRequest(texts, OllamaEmbeddingOptions.builder().model(modelId).build()))
          .getResults()
          .stream()
          .map(Embedding::getOutput)
          .toList();
    } catch (RuntimeException e) {
      throw new LocalModelUnavailableException("ollama-embedding-unavailable");
    }
  }
}
