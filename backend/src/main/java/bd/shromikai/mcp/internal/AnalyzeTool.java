package bd.shromikai.mcp.internal;

import bd.shromikai.analysis.ExternalAnalysisService;
import bd.shromikai.analysis.Side;
import java.util.Set;
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

  AnalyzeTool(ExternalAnalysisService analyses, McpProperties properties) {
    this.analyses = analyses;
    this.properties = properties;
  }

  McpCallGuard.Output analyse(Args args, java.util.UUID keyId, java.util.UUID orgId) {
    String text = args.text("text", 200, 60_000);
    Side side = Side.valueOf(args.oneOf("side", Set.of("WORKER", "EMPLOYER"), null));
    String language = args.oneOf("output_language", Set.of("bn", "en"), "bn");
    ExternalAnalysisService.Result result =
        analyses.analyse(keyId, orgId, text, side, language, properties.analyzeTimeout());
    return new McpCallGuard.Output(result.content(), result.costUsdMicros());
  }
}
