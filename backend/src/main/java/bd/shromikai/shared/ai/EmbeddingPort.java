package bd.shromikai.shared.ai;

import java.util.List;

/** Text embeddings (bge-m3 on Ollama in production, {@code FakeEmbeddingPort} in tests). */
public interface EmbeddingPort {

  String modelId();

  int dimensions();

  /** One vector per input, in input order. */
  List<float[]> embed(List<String> texts);
}
