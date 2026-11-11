package bd.shromikai.testsupport;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.ai.PromptRenderer;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.config.ShromikaiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Function;

/** Builds a {@link ModelGateway} over hand-written chat ports (no Spring context). */
public final class TestGateways {

  public static final ObjectMapper MAPPER = new ObjectMapper();

  private TestGateways() {}

  public static ModelGateway gateway(ChatPort... ports) {
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            null,
            "claude-haiku-5-5",
            null,
            "qwen2.5:7b-instruct",
            "bge-m3");
    return new ModelGateway(
        new PromptRegistry(MAPPER), new PromptRenderer(MAPPER), List.of(ports), models);
  }

  /** A port that answers every request with the function's JSON. */
  public static ChatPort port(String provider, Function<ChatRequest, JsonNode> answer) {
    return new ChatPort() {
      @Override
      public String provider() {
        return provider;
      }

      @Override
      public ChatResponse call(ChatRequest request) {
        return new ChatResponse(answer.apply(request), "stub-" + provider, TokenUsage.NONE, false);
      }
    };
  }

  public static JsonNode json(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
