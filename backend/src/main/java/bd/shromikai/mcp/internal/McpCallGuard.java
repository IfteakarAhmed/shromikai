package bd.shromikai.mcp.internal;

import bd.shromikai.auth.ApiKeyPrincipal;
import bd.shromikai.auth.UsageService;
import bd.shromikai.law.LawReadService;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Everything every MCP call has in common (FR-65, T-12): who is calling (the API key the transport
 * context carries), the per-key limit, problem documents as tool errors, the disclaimer and corpus
 * release in every result (R-12), and one {@code usage_event} per call. A refused call is audited
 * and not metered. No argument, query, claim or result is logged here (R-02).
 */
@Component
@Profile("mcp")
public final class McpCallGuard {

  /** The key of the principal in the transport context. */
  public static final String PRINCIPAL = "shromikai.apiKey";

  static final String DISCLAIMER = "এটা আইনি পরামর্শ নয়। This is not legal advice.";

  private static final Logger LOG = LoggerFactory.getLogger(McpCallGuard.class);
  private static final int PURGE_ABOVE = 20_000;

  /** What a tool produced and what it cost. */
  record Output(ObjectNode content, long costUsdMicros) {
    Output(ObjectNode content) {
      this(content, 0);
    }
  }

  /** A tool body. */
  @FunctionalInterface
  interface Body {
    Output run(ApiKeyPrincipal key) throws Exception;
  }

  private final McpProperties properties;
  private final UsageService usage;
  private final AuditService audit;
  private final LawReadService law;
  private final ObjectMapper mapper;
  private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();

  McpCallGuard(
      McpProperties properties,
      UsageService usage,
      AuditService audit,
      LawReadService law,
      ObjectMapper mapper) {
    this.properties = properties;
    this.usage = usage;
    this.audit = audit;
    this.law = law;
    this.mapper = mapper;
  }

  /** Runs a tool call: admission, body, problem mapping, metering. */
  McpSchema.CallToolResult tool(McpSyncServerExchange exchange, Body body) {
    ApiKeyPrincipal key;
    try {
      key = admit(exchange.transportContext());
    } catch (ProblemException refused) {
      return error(refused);
    }
    long cost = 0;
    try {
      Output output = body.run(key);
      cost = output.costUsdMicros();
      return success(output.content());
    } catch (ProblemException problem) {
      return error(problem);
    } catch (Exception unexpected) {
      LOG.error("mcp tool failed: {}", unexpected.getClass().getSimpleName());
      return error(new ProblemException(ProblemType.INTERNAL_ERROR));
    } finally {
      meter(key, cost);
    }
  }

  /**
   * The key of the call, after the rate limit. Throws {@code rate-limited} (audited, not metered)
   * or {@code invalid-token} when the transport carried no key.
   */
  ApiKeyPrincipal admit(McpTransportContext context) {
    Object principal = context == null ? null : context.get(PRINCIPAL);
    if (!(principal instanceof ApiKeyPrincipal key)) {
      throw new ProblemException(ProblemType.INVALID_TOKEN);
    }
    if (buckets.size() > PURGE_ABOVE) {
      buckets.values().removeIf(b -> b.getAvailableTokens() >= properties.rateLimitPerMinute());
    }
    Bucket bucket =
        buckets.computeIfAbsent(
            key.keyId(),
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(properties.rateLimitPerMinute())
                            .refillIntervally(
                                properties.rateLimitPerMinute(), Duration.ofMinutes(1))
                            .build())
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      audit.record(
          AuditEvent.of(
                  key.keyId(),
                  ActorKind.API_KEY,
                  AuditAction.ACCESS_DENIED,
                  Map.of("reason", "rate_limited"))
              .withTarget("mcp", null));
      long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
      throw new ProblemException(ProblemType.RATE_LIMITED, null, (int) seconds);
    }
    return key;
  }

  void meter(ApiKeyPrincipal key, long costUsdMicros) {
    try {
      usage.record(
          key.orgId(),
          key.keyId(),
          null,
          UsageService.Kind.MCP_CALL,
          1,
          Math.max(0, costUsdMicros));
    } catch (RuntimeException e) {
      LOG.error("usage event not written: {}", e.getClass().getSimpleName());
    }
  }

  McpSchema.CallToolResult success(ObjectNode content) {
    content.put("disclaimer", DISCLAIMER);
    if (!content.has("corpus_release")) {
      content.put("corpus_release", law.corpusRelease());
    }
    return McpSchema.CallToolResult.builder()
        .structuredContent(content)
        .addTextContent(content.toString())
        .isError(false)
        .build();
  }

  McpSchema.CallToolResult error(ProblemException problem) {
    ObjectNode body = mapper.createObjectNode();
    ProblemType type = problem.type();
    body.put("type", type.typeUri());
    body.put("title", type.title(Locale.ENGLISH));
    body.put("status", type.status());
    body.put("code", type.code());
    if (problem.detail() != null) {
      body.put("detail", problem.detail());
    }
    if (problem.retryAfterSeconds() != null) {
      body.put("retryAfterSeconds", problem.retryAfterSeconds());
    }
    body.put("disclaimer", DISCLAIMER);
    return McpSchema.CallToolResult.builder()
        .structuredContent(body)
        .addTextContent(body.toString())
        .isError(true)
        .build();
  }
}
