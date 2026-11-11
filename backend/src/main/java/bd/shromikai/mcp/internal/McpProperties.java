package bd.shromikai.mcp.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code shromikai.mcp.*}: the per-key limit of calls per minute (FR-65, api.md §8) and how long
 * {@code analyze_employment_contract} may run before it answers {@code analysis-timeout} (FR-66),
 * and how many analyses one key may start a day. The daily count is per instance and in memory,
 * like the per-minute limit.
 */
@Validated
@ConfigurationProperties("shromikai.mcp")
public record McpProperties(
    @Min(1) @Max(10_000) int rateLimitPerMinute,
    @NotNull Duration analyzeTimeout,
    @Min(1) @Max(1_000) int analyzePerKeyPerDay) {

  public McpProperties {
    if (analyzeTimeout == null) {
      analyzeTimeout = Duration.ofSeconds(120);
    }
    if (analyzePerKeyPerDay == 0) {
      analyzePerKeyPerDay = 10;
    }
  }
}
