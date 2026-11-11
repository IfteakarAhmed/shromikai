package bd.shromikai.shared.ai.gemini;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRequestRejectedException;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.ai.internal.Backoff;
import bd.shromikai.shared.ai.internal.RetryAfter;
import bd.shromikai.shared.config.ShromikaiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini verifier and judge through a {@code RestClient} (ADR-0005): JSON response mode with a
 * response schema, temperature 0, API key in the {@code x-goog-api-key} header (never in the URL).
 */
public final class GeminiChatAdapter implements ChatPort {

  private static final Set<String> BLOCKED_FINISH =
      Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII");

  private final EgressGuard egressGuard;
  private final ShromikaiProperties.Provider provider;
  private final ObjectMapper mapper;
  private final Backoff backoff;
  private final HttpClient httpClient;

  public GeminiChatAdapter(
      EgressGuard egressGuard,
      ShromikaiProperties.Provider provider,
      ObjectMapper mapper,
      Backoff backoff) {
    this.egressGuard = Objects.requireNonNull(egressGuard, "an egress guard is required (R-03)");
    this.provider = provider;
    this.mapper = mapper;
    this.backoff = backoff;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public String provider() {
    return "gemini";
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    egressGuard.check(request);
    if (!provider.configured() || request.modelId().isBlank()) {
      throw new UpstreamUnavailableException(
          "gemini-not-configured", UpstreamUnavailableException.NOT_CONFIGURED, null);
    }
    ObjectNode body = body(request);
    JsonNode response = backoff.call(() -> post(request, body));
    return toResponse(request, response);
  }

  ObjectNode body(ChatRequest request) {
    ObjectNode body = mapper.createObjectNode();
    body.putObject("systemInstruction").putArray("parts").addObject().put("text", request.system());
    ObjectNode content = body.putArray("contents").addObject();
    content.put("role", "user");
    content.putArray("parts").addObject().put("text", request.user().reveal());
    ObjectNode config = body.putObject("generationConfig");
    config.put("responseMimeType", "application/json");
    config.set("responseJsonSchema", schemaForGemini(request.outputSchema()));
    config.put("temperature", request.temperature() == null ? 0.0 : request.temperature());
    config.put("maxOutputTokens", request.maxOutputTokens());
    if (request.modelId().startsWith("gemini-2.5")) {
      // 2.5 Flash thinks by default and thinking tokens count against maxOutputTokens; a short
      // JSON verdict needs none. Later families take other thinking settings and are left alone.
      config.putObject("thinkingConfig").put("thinkingBudget", 0);
    }
    return body;
  }

  /** Gemini's response schema rejects the {@code $schema} and {@code $id} keywords. */
  private static JsonNode schemaForGemini(JsonNode schema) {
    if (!(schema instanceof ObjectNode object)) {
      return schema;
    }
    ObjectNode copy = object.deepCopy();
    copy.remove("$schema");
    copy.remove("$id");
    return copy;
  }

  private JsonNode post(ChatRequest request, ObjectNode body) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(request.timeout());
    RestClient client =
        RestClient.builder().baseUrl(provider.baseUrl()).requestFactory(factory).build();
    try {
      return client
          .post()
          .uri("/v1beta/models/{model}:generateContent", request.modelId())
          .header("x-goog-api-key", provider.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      if (ModelRequestRejectedException.applies(status)) {
        throw new ModelRequestRejectedException("gemini-http-" + status, status);
      }
      HttpHeaders headers = e.getResponseHeaders();
      String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
      throw new UpstreamUnavailableException(
          "gemini-http-" + status, status, RetryAfter.forStatus(status, retryAfter, Instant.now()));
    } catch (ResourceAccessException e) {
      if (e.getCause() instanceof HttpTimeoutException
          || e.getCause() instanceof SocketTimeoutException) {
        throw new ModelTimeoutException("gemini-timeout");
      }
      throw new UpstreamUnavailableException("gemini-connection", 0, null);
    }
  }

  private ChatResponse toResponse(ChatRequest request, JsonNode response) {
    if (response == null) {
      throw new ModelOutputInvalidException("gemini-empty-response");
    }
    if (!response.path("promptFeedback").path("blockReason").isMissingNode()) {
      throw new ModelRefusedException("gemini-prompt-blocked");
    }
    JsonNode candidate = response.path("candidates").path(0);
    String finishReason = candidate.path("finishReason").asText("");
    if (BLOCKED_FINISH.contains(finishReason)) {
      throw new ModelRefusedException("gemini-refusal");
    }
    if ("MAX_TOKENS".equals(finishReason)) {
      throw new ModelOutputInvalidException("gemini-max-tokens");
    }
    StringBuilder text = new StringBuilder();
    candidate
        .path("content")
        .path("parts")
        .forEach(part -> text.append(part.path("text").asText()));
    JsonNode output;
    try {
      output = mapper.readTree(text.toString());
    } catch (JsonProcessingException e) {
      throw new ModelOutputInvalidException("gemini-output-not-json");
    }
    if (output == null || output.isMissingNode()) {
      throw new ModelOutputInvalidException("gemini-output-not-json");
    }
    JsonNode usage = response.path("usageMetadata");
    return new ChatResponse(
        output,
        response.path("modelVersion").asText(request.modelId()),
        new TokenUsage(
            usage.path("promptTokenCount").asLong(),
            // Thinking tokens are billed as output tokens.
            usage.path("candidatesTokenCount").asLong() + usage.path("thoughtsTokenCount").asLong(),
            usage.path("cachedContentTokenCount").asLong()),
        false);
  }
}
