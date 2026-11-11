package bd.shromikai.mcp.internal;

import bd.shromikai.analysis.ExternalAnalysisService;
import bd.shromikai.analysis.Side;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code analyze_employment_contract} (FR-66): the full pipeline with the privacy shield on the
 * server, run synchronously for up to {@code analyze-timeout}. The result keeps placeholders, never
 * echoes the contract and stores nothing ({@link ExternalAnalysisService}).
 */
@Component
@Profile("mcp")
final class AnalyzeTool {

  private final ExternalAnalysisService analyses;
  private final McpProperties properties;
  private final ConcurrentHashMap<UUID, Bucket> daily = new ConcurrentHashMap<>();

  AnalyzeTool(ExternalAnalysisService analyses, McpProperties properties) {
    this.analyses = analyses;
    this.properties = properties;
  }

  McpCallGuard.Output analyse(Args args, java.util.UUID keyId, java.util.UUID orgId) {
    String text = args.text("text", 200, 60_000);
    Side side = Side.valueOf(args.oneOf("side", Set.of("WORKER", "EMPLOYER"), null));
    String language = args.oneOf("output_language", Set.of("bn", "en"), "bn");
    spendDailyAnalysis(keyId);
    ExternalAnalysisService.Result result =
        analyses.analyse(keyId, orgId, text, side, language, properties.analyzeTimeout());
    return new McpCallGuard.Output(result.content(), result.costUsdMicros());
  }

  /** One analysis of the key's daily allowance, or {@code quota-exceeded} with Retry-After. */
  private void spendDailyAnalysis(UUID keyId) {
    int cap = properties.analyzePerKeyPerDay();
    Bucket bucket =
        daily.computeIfAbsent(
            keyId,
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(cap)
                            .refillIntervally(cap, Duration.ofDays(1))
                            .build())
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
      throw new ProblemException(
          ProblemType.QUOTA_EXCEEDED,
          "this API key has used its analyses for today",
          (int) Math.min(seconds, Integer.MAX_VALUE));
    }
  }
}
