package bd.shromikai.shared.ai.ollama;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.TokenUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Local chat on Ollama through Spring AI (ADR-0005): {@code format} = the prompt's JSON Schema,
 * temperature 0 unless the prompt sets another number. Ollama is a local call, so no egress guard
 * runs; any failure to reach it fails closed with {@code local-model-unavailable} (R-03).
 */
public final class OllamaChatAdapter implements ChatPort {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final ChatModel chatModel;
  private final ObjectMapper mapper;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public OllamaChatAdapter(ChatModel chatModel, ObjectMapper mapper) {
    this.chatModel = chatModel;
    this.mapper = mapper;
  }

  @Override
  public String provider() {
    return "ollama";
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    OllamaChatOptions options =
        OllamaChatOptions.builder()
            .model(request.modelId())
            .format(mapper.convertValue(request.outputSchema(), MAP))
            .temperature(request.temperature() == null ? 0.0 : request.temperature())
            .numPredict(request.maxOutputTokens())
            .build();
    Prompt prompt =
        new Prompt(
            List.of(new SystemMessage(request.system()), new UserMessage(request.user().reveal())),
            options);
    Future<org.springframework.ai.chat.model.ChatResponse> future =
        executor.submit(() -> chatModel.call(prompt));
    org.springframework.ai.chat.model.ChatResponse response;
    try {
      response = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new ModelTimeoutException("ollama-timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LocalModelUnavailableException("ollama-interrupted");
    } catch (ExecutionException e) {
      throw new LocalModelUnavailableException("ollama-unavailable");
    }
    String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
    if (text == null || text.isBlank()) {
      throw new ModelOutputInvalidException("ollama-empty-output");
    }
    JsonNode output;
    try {
      output = mapper.readTree(text);
    } catch (JsonProcessingException e) {
      throw new ModelOutputInvalidException("ollama-output-not-json");
    }
    Usage usage = response.getMetadata().getUsage();
    return new ChatResponse(
        output,
        request.modelId(),
        new TokenUsage(tokens(usage.getPromptTokens()), tokens(usage.getCompletionTokens()), 0),
        false);
  }

  private static long tokens(Integer value) {
    return value == null ? 0 : value;
  }
}
