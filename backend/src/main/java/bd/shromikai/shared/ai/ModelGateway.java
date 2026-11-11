package bd.shromikai.shared.ai;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.PromptRenderer.Rendered;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.json.JsonSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The one way to call a model with a runtime prompt (R-04, R-05): renders the prompt, picks the
 * provider and model of the prompt's role, calls the port, and returns only output that validates
 * against the prompt's JSON Schema. On invalid output it repairs once, sending the validator
 * errors; a second failure throws {@link ModelOutputInvalidException}. The egress guard runs inside
 * every cloud adapter.
 */
@Service
public class ModelGateway {

  private final PromptRegistry registry;
  private final PromptRenderer renderer;
  private final Map<String, ChatPort> ports = new HashMap<>();
  private final ShromikaiProperties.Models models;

  @Autowired
  public ModelGateway(
      PromptRegistry registry,
      PromptRenderer renderer,
      List<ChatPort> chatPorts,
      ShromikaiProperties properties) {
    this(registry, renderer, chatPorts, properties.models());
  }

  public ModelGateway(
      PromptRegistry registry,
      PromptRenderer renderer,
      List<ChatPort> chatPorts,
      ShromikaiProperties.Models models) {
    this.registry = registry;
    this.renderer = renderer;
    this.models = models;
    chatPorts.forEach(port -> ports.put(port.provider(), port));
  }

  /** A validated model output and the prompt version that produced it. */
  public record Result(JsonNode output, String promptId, int promptVersion, String modelId) {}

  /** Calls the latest version of the prompt. */
  public Result call(String promptId, Map<String, ?> variables) {
    return call(registry.latest(promptId), variables);
  }

  public Result call(Prompt prompt, Map<String, ?> variables) {
    Rendered rendered = renderer.render(prompt, variables);
    ChatPort port = port(prompt.role());
    String modelId = modelId(prompt.role());
    ChatResponse response = null;
    List<String> errors;
    try {
      response = port.call(request(prompt, modelId, rendered.system(), rendered.user()));
      errors = JsonSchemas.validate(prompt.outputSchema(), response.output());
      if (errors.isEmpty()) {
        return result(prompt, response);
      }
    } catch (ModelOutputInvalidException e) {
      errors = List.of(e.reasonCode());
    }
    String repair =
        rendered.user().reveal()
            + "\n\n<validation_errors>\nYour previous answer was invalid. Answer again with JSON"
            + " that satisfies the schema. Errors: "
            + PromptRenderer.escapeForRepair(String.join("; ", errors))
            + "\n</validation_errors>";
    ChatResponse second;
    try {
      second = port.call(request(prompt, modelId, rendered.system(), SensitiveText.of(repair)));
    } catch (ModelOutputInvalidException e) {
      throw new ModelOutputInvalidException("schema-invalid-after-repair");
    }
    if (!JsonSchemas.validate(prompt.outputSchema(), second.output()).isEmpty()) {
      throw new ModelOutputInvalidException("schema-invalid-after-repair");
    }
    return result(prompt, second);
  }

  private static Result result(Prompt prompt, ChatResponse response) {
    return new Result(response.output(), prompt.id(), prompt.version(), response.modelId());
  }

  private static ChatRequest request(
      Prompt prompt, String modelId, String system, SensitiveText user) {
    return new ChatRequest(
        prompt.id(),
        prompt.version(),
        prompt.role(),
        modelId,
        system,
        user,
        prompt.outputSchema(),
        prompt.maxOutputTokens(),
        Duration.ofSeconds(prompt.timeoutSeconds()),
        prompt.temperature(),
        prompt.effort());
  }

  private ChatPort port(ModelRole role) {
    ChatPort fake = ports.get("fake");
    if (fake != null) {
      return fake;
    }
    String provider =
        switch (role) {
          case FAST, REASONING, VERIFIER_FALLBACK -> "anthropic";
          case VERIFIER, JUDGE -> "gemini";
          case LOCAL_PII -> "ollama";
          case EMBED -> throw new IllegalArgumentException("EMBED is not a chat role");
        };
    ChatPort port = ports.get(provider);
    if (port == null) {
      throw new IllegalStateException("No chat port for provider " + provider);
    }
    return port;
  }

  private String modelId(ModelRole role) {
    String id =
        switch (role) {
          case FAST -> models.fast();
          case REASONING -> models.reasoning();
          case VERIFIER -> models.verifierOrNull();
          case VERIFIER_FALLBACK -> models.verifierFallback();
          case JUDGE -> models.judgeOrNull();
          case LOCAL_PII -> models.localPii();
          case EMBED -> models.embedding();
        };
    if (id == null) {
      throw new IllegalStateException("No model configured for role " + role);
    }
    return id;
  }
}
