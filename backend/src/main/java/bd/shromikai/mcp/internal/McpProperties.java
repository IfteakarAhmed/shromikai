package bd.shromikai.mcp.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code shromikai.mcp.*}: the per-key limit of calls per minute (FR-65, api.md §8) and how long
 * {@code analyze_employment_contract} may run before it answers {@code analysis-timeout} (FR-66).
 */
@Validated
@ConfigurationProperties("shromikai.mcp")
public record McpProperties(
    @Min(1) @Max(10_000) int rateLimitPerMinute, @NotNull Duration analyzeTimeout) {

  public McpProperties {
    if (analyzeTimeout == null) {
      analyzeTimeout = Duration.ofSeconds(120);
    }
  }
}
