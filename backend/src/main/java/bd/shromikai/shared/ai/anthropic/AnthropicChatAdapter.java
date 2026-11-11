package bd.shromikai.shared.ai.anthropic;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRequestRejectedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.ai.internal.RetryAfter;
import bd.shromikai.shared.config.ShromikaiProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InterruptedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Claude through the official Anthropic Java SDK (ADR-0005): structured output with the prompt's
 * JSON Schema, {@code output_config.effort}, no sampling parameters, refusal mapping, and
 * server-side refusal fallbacks on {@code REASONING} calls. The egress guard runs before anything
 * is sent.
 */
public final class AnthropicChatAdapter implements ChatPort {

  static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  private final EgressGuard egressGuard;
  private final ShromikaiProperties.Provider provider;
  private final ObjectMapper mapper;
  private volatile AnthropicClient client;

  public AnthropicChatAdapter(
      EgressGuard egressGuard, ShromikaiProperties.Provider provider, ObjectMapper mapper) {
    this.egressGuard = Objects.requireNonNull(egressGuard, "an egress guard is required (R-03)");
    this.provider = provider;
    this.mapper = mapper;
  }

  @Override
  public String provider() {
    return "anthropic";
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    egressGuard.check(request);
    if (request.temperature() != null) {
      throw new IllegalArgumentException("Claude rejects sampling parameters (ADR-0005)");
    }
    MessageCreateParams params = params(request);
    Message message;
    try {
      message =
          client()
              .messages()
              .create(params, RequestOptions.builder().timeout(request.timeout()).build());
    } catch (AnthropicServiceException e) {
      int status = e.statusCode();
      if (ModelRequestRejectedException.applies(status)) {
        throw new ModelRequestRejectedException("anthropic-http-" + status + reason(e), status);
      }
      String retryAfter = e.headers().values("retry-after").stream().findFirst().orElse(null);
      throw new UpstreamUnavailableException(
          "anthropic-http-" + status,
          status,
          RetryAfter.forStatus(status, retryAfter, Instant.now()));
    } catch (AnthropicIoException e) {
      if (e.getCause() instanceof InterruptedIOException) {
        throw new ModelTimeoutException("anthropic-timeout");
      }
      throw new UpstreamUnavailableException("anthropic-connection", 0, null);
    }
    if (message.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
      throw new ModelRefusedException("anthropic-refusal");
    }
    if (message.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
      throw new ModelOutputInvalidException("anthropic-max-tokens", usage(message));
    }
    return new ChatResponse(parse(message), message.model().asString(), usage(message), false);
  }

  private static TokenUsage usage(Message message) {
    return new TokenUsage(
        message.usage().inputTokens(),
        message.usage().outputTokens(),
        message.usage().cacheReadInputTokens().orElse(0L),
        message.usage().cacheCreationInputTokens().orElse(0L));
  }

  MessageCreateParams params(ChatRequest request) {
    OutputConfig.Builder outputConfig =
        OutputConfig.builder()
            .format(
                JsonOutputFormat.builder()
                    .schema(
                        JsonOutputFormat.Schema.builder()
                            .additionalProperties(schemaProperties(request.outputSchema()))
                            .build())
                    .build());
    if (request.effort() != null) {
      outputConfig.effort(OutputConfig.Effort.of(request.effort().name().toLowerCase()));
    }
    // Extended thinking is off. Its tokens count against max_tokens, and a live run showed Haiku
    // spending 90 to 550 hidden tokens on a 150-token answer, so a prompt's output cap was hit at
    // random and the call failed (ADR-0015 decision 10, architecture §18.6 C38).
    MessageCreateParams.Builder builder =
        MessageCreateParams.builder()
            .model(request.modelId())
            .maxTokens(request.maxOutputTokens())
            // The stable system prompt (rubric, rules, few-shot examples) is cacheable with the
            // 5-minute TTL (architecture §6 adapter rule 3); the API ignores it below the model's
            // minimum cacheable length, so short prompts are unaffected.
            .systemOfTextBlockParams(
                List.of(
                    TextBlockParam.builder()
                        .text(request.system())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
            .addUserMessage(request.user().reveal())
            .outputConfig(outputConfig.build())
            .putAdditionalBodyProperty(
                "thinking", JsonValue.from(Map.of("type", thinkingOff(request.role()))));
    if (request.role() == ModelRole.REASONING) {
      builder
          .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
          .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }
    return builder.build();
  }

  /**
   * How a model is told not to think. Haiku 5.5 takes {@code disabled}; Sonnet 5.5 answers a 400
   * ("send between_tools instead of disabled": it never thinks before responding, the setting only
   * covers the notes it writes between tool calls). Found in a live run.
   */
  static String thinkingOff(ModelRole role) {
    return role == ModelRole.REASONING ? "between_tools" : "disabled";
  }

  /**
   * The provider's own error text (for example which request field it rejected), for logs and
   * traces only; it never becomes the client-facing detail (NFR-04). The text describes the request
   * shape, not its content.
   */
  private static String reason(AnthropicServiceException e) {
    String message =
        e.getMessage() == null ? "" : e.getMessage().replaceAll("[\\p{Cntrl}]", " ").strip();
    return message.isEmpty() ? "" : " " + message.substring(0, Math.min(message.length(), 300));
  }

  private static Map<String, JsonValue> schemaProperties(JsonNode schema) {
    return schema.properties().stream()
        .filter(entry -> !entry.getKey().equals("$schema") && !entry.getKey().equals("$id"))
        .collect(
            Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.fromJsonNode(entry.getValue())));
  }

  private JsonNode parse(Message message) {
    String text =
        message.content().stream()
            .map(ContentBlock::text)
            .flatMap(java.util.Optional::stream)
            .map(TextBlock::text)
            .collect(Collectors.joining());
    try {
      return mapper.readTree(text);
    } catch (JsonProcessingException e) {
      throw new ModelOutputInvalidException("anthropic-output-not-json");
    }
  }

  private AnthropicClient client() {
    AnthropicClient current = client;
    if (current == null) {
      synchronized (this) {
        current = client;
        if (current == null) {
          if (!provider.configured()) {
            throw new UpstreamUnavailableException(
                "anthropic-not-configured", UpstreamUnavailableException.NOT_CONFIGURED, null);
          }
          current =
              AnthropicOkHttpClient.builder()
                  .apiKey(provider.apiKey())
                  // Workspace API keys authenticate with x-api-key; setting it explicitly keeps
                  // the SDK from sending the key as a Bearer token.
                  .putHeader("x-api-key", provider.apiKey())
                  .baseUrl(provider.baseUrl())
                  .maxRetries(2)
                  .build();
          client = current;
        }
      }
    }
    return current;
  }
}
