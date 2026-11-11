package bd.shromikai.shared.ai;

import bd.shromikai.shared.ai.anthropic.AnthropicChatAdapter;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.ai.fake.FakeEmbeddingPort;
import bd.shromikai.shared.ai.gemini.GeminiChatAdapter;
import bd.shromikai.shared.ai.internal.Backoff;
import bd.shromikai.shared.ai.ollama.OllamaChatAdapter;
import bd.shromikai.shared.ai.ollama.OllamaEmbeddingAdapter;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.json.JsonSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the model ports. Real adapters outside the {@code test} and {@code fake-models} profiles;
 * deterministic fakes inside them (architecture §16.3).
 */
@Configuration(proxyBeanMethods = false)
public class AiConfiguration {

  @Bean
  JsonSchemas jsonSchemas() {
    return new JsonSchemas();
  }

  @Configuration(proxyBeanMethods = false)
  @Profile("!test & !fake-models")
  static class RealModels {

    @Bean
    AnthropicChatAdapter anthropicChatAdapter(
        EgressGuard egressGuard, ShromikaiProperties properties, ObjectMapper mapper) {
      return new AnthropicChatAdapter(egressGuard, properties.providers().anthropic(), mapper);
    }

    @Bean
    GeminiChatAdapter geminiChatAdapter(
        EgressGuard egressGuard, ShromikaiProperties properties, ObjectMapper mapper) {
      return new GeminiChatAdapter(
          egressGuard, properties.providers().gemini(), mapper, new Backoff());
    }

    @Bean
    OllamaChatAdapter ollamaChatAdapter(ChatModel ollamaChatModel, ObjectMapper mapper) {
      return new OllamaChatAdapter(ollamaChatModel, mapper);
    }

    @Bean
    OllamaEmbeddingAdapter ollamaEmbeddingAdapter(
        EmbeddingModel embeddingModel, ShromikaiProperties properties) {
      return new OllamaEmbeddingAdapter(embeddingModel, properties.models().embedding());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Profile({"test", "fake-models"})
  static class FakeModels {

    @Bean
    FakeChatPort fakeChatPort(
        ShromikaiProperties properties, ObjectMapper mapper, JsonSchemas schemas) {
      return new FakeChatPort(
          properties.fakeModels().fixtures(),
          mapper,
          schemas,
          properties.fakeModels().defaultCase());
    }

    @Bean
    FakeEmbeddingPort fakeEmbeddingPort(ShromikaiProperties properties) {
      return new FakeEmbeddingPort(properties.models().embedding());
    }
  }
}
