package bd.shromikai.shared.ai;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.PromptRenderer.Rendered;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.json.JsonSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
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
  private final List<LlmCallListener> listeners;

  @Autowired
  public ModelGateway(
      PromptRegistry registry,
      PromptRenderer renderer,
      List<ChatPort> chatPorts,
      ShromikaiProperties properties,
      ObjectProvider<LlmCallListener> listeners) {
    this(registry, renderer, chatPorts, properties.models(), listeners.orderedStream().toList());
  }

  public ModelGateway(
      PromptRegistry registry,
      PromptRenderer renderer,
      List<ChatPort> chatPorts,
      ShromikaiProperties.Models models) {
    this(registry, renderer, chatPorts, models, List.of());
  }

  public ModelGateway(
      PromptRegistry registry,
      PromptRenderer renderer,
      List<ChatPort> chatPorts,
      ShromikaiProperties.Models models,
      List<LlmCallListener> listeners) {
    this.registry = registry;
    this.renderer = renderer;
    this.models = models;
    this.listeners = List.copyOf(listeners);
    chatPorts.forEach(port -> ports.put(port.provider(), port));
  }

  /**
   * A validated model output, the prompt version that produced it and what the call cost in tokens
   * and time (a repair attempt is included).
   */
  public record Result(
      JsonNode output,
      String promptId,
      int promptVersion,
      String modelId,
      TokenUsage usage,
      long latencyMs) {}

  /** Calls the latest version of the prompt. */
  public Result call(String promptId, Map<String, ?> variables) {
    return call(registry.latest(promptId), variables, null);
  }

  /** Calls the latest version of the prompt for a job whose placeholder values the guard checks. */
  public Result call(String promptId, Map<String, ?> variables, @Nullable EgressContext context) {
    return call(registry.latest(promptId), variables, context);
  }

  public Result call(Prompt prompt, Map<String, ?> variables) {
    return call(prompt, variables, null);
  }

  public Result call(Prompt prompt, Map<String, ?> variables, @Nullable EgressContext context) {
    return call(prompt, variables, context, Map.of());
  }

  /**
   * Calls the latest version of a prompt with per-request enums (architecture §8.3): for every
   * {@code dynamic_enums} path of the prompt, {@code enums} holds the allowed values; the output
   * schema is restricted to them before the call and the answer is validated against the restricted
   * schema.
   */
  public Result call(
      String promptId,
      Map<String, ?> variables,
      @Nullable EgressContext context,
      Map<String, ? extends Collection<String>> enums) {
    return call(registry.latest(promptId), variables, context, enums);
  }

  public Result call(
      Prompt prompt,
      Map<String, ?> variables,
      @Nullable EgressContext context,
      Map<String, ? extends Collection<String>> enums) {
    long start = System.nanoTime();
    Prompt effective = restricted(prompt, enums);
    Rendered rendered = renderer.render(effective, variables);
    ChatPort port = port(effective.role());
    String modelId = modelId(effective.role());
    TokenUsage usage = TokenUsage.NONE;
    boolean recorded = false;
    String answeredBy = modelId;
    ChatResponse response = null;
    List<String> errors;
    try {
      response =
          port.call(request(effective, modelId, rendered.system(), rendered.user(), context));
      usage = usage.plus(response.usage());
      recorded = response.recorded();
      answeredBy = response.modelId();
      errors = JsonSchemas.validate(effective.outputSchema(), response.output());
      if (errors.isEmpty()) {
        return finish(effective, response, usage, start, recorded);
      }
    } catch (ModelOutputInvalidException e) {
      usage = usage.plus(e.usage());
      if (e.stoppedAtCap()) {
        // The same request would stop at the same cap; the caller splits the batch instead.
        report(effective, modelId, usage, start, false);
        throw new ModelOutputInvalidException(e.reasonCode(), usage);
      }
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
      second =
          port.call(
              request(effective, modelId, rendered.system(), SensitiveText.of(repair), context));
    } catch (ModelOutputInvalidException e) {
      usage = usage.plus(e.usage());
      report(effective, answeredBy, usage, start, recorded);
      throw new ModelOutputInvalidException(
          e.stoppedAtCap() ? e.reasonCode() : "schema-invalid-after-repair", usage);
    }
    usage = usage.plus(second.usage());
    if (!JsonSchemas.validate(effective.outputSchema(), second.output()).isEmpty()) {
      report(effective, second.modelId(), usage, start, second.recorded());
      throw new ModelOutputInvalidException("schema-invalid-after-repair", usage);
    }
    return finish(effective, second, usage, start, second.recorded());
  }

  private Prompt restricted(Prompt prompt, Map<String, ? extends Collection<String>> enums) {
    if (prompt.dynamicEnums().isEmpty()) {
      return prompt;
    }
    JsonNode schema = prompt.outputSchema();
    for (DynamicEnum declared : prompt.dynamicEnums()) {
      Collection<String> values = enums.get(declared.path());
      if (values == null) {
        throw new IllegalArgumentException("Missing dynamic enum values for " + declared.path());
      }
      schema = SchemaEnums.restrict(schema, declared.path(), values);
    }
    return new Prompt(
        prompt.id(),
        prompt.version(),
        prompt.status(),
        prompt.role(),
        prompt.temperature(),
        prompt.effort(),
        prompt.maxOutputTokens(),
        prompt.timeoutSeconds(),
        prompt.variables(),
        prompt.outputSchemaLocation(),
        schema,
        prompt.system(),
        prompt.user(),
        prompt.dynamicEnums());
  }

  private Result finish(
      Prompt prompt, ChatResponse response, TokenUsage usage, long startNanos, boolean recorded) {
    long latencyMs = report(prompt, response.modelId(), usage, startNanos, recorded);
    return new Result(
        response.output(), prompt.id(), prompt.version(), response.modelId(), usage, latencyMs);
  }

  private long report(
      Prompt prompt, String modelId, TokenUsage usage, long startNanos, boolean recorded) {
    long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
    LlmCall call =
        new LlmCall(
            prompt.id(), prompt.version(), prompt.role(), modelId, usage, latencyMs, recorded);
    for (LlmCallListener listener : listeners) {
      try {
        listener.onCall(call);
      } catch (RuntimeException ignored) {
        // A listener must never break an analysis.
      }
    }
    return latencyMs;
  }

  private static ChatRequest request(
      Prompt prompt,
      String modelId,
      String system,
      SensitiveText user,
      @Nullable EgressContext context) {
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
        prompt.effort(),
        context);
  }

  /** The model that serves the latest version of a prompt, for accounting a failed call. */
  public String modelIdFor(String promptId) {
    return modelId(registry.latest(promptId).role());
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
