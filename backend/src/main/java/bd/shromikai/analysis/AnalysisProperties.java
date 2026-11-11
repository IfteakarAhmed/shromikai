package bd.shromikai.analysis;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pipeline timing and budget (architecture §5.3, §12). {@code stepTimeouts} are per-step cancel
 * limits, deliberately above the p95 budgets of §5.3; {@code hardTimeout} is the wall time of one
 * analysis ({@code analysis-timeout}). The budget keys cap cost and tokens of one analysis ({@code
 * budget-exceeded}).
 */
@ConfigurationProperties("shromikai")
public record AnalysisProperties(Analysis analysis, Budget budget) {

  /**
   * @param jurisdictions the jurisdictions of the active pack that citations may come from
   */
  public record Analysis(
      Duration hardTimeout, Map<String, Duration> stepTimeouts, Set<String> jurisdictions) {}

  public record Budget(double perAnalysisUsd, long perAnalysisTokens) {

    public long perAnalysisUsdMicros() {
      return Math.round(perAnalysisUsd * 1_000_000);
    }
  }

  public AnalysisProperties {
    if (analysis == null) {
      analysis = new Analysis(Duration.ofSeconds(120), Map.of(), Set.of("BD"));
    }
    if (analysis.jurisdictions() == null || analysis.jurisdictions().isEmpty()) {
      analysis = new Analysis(analysis.hardTimeout(), analysis.stepTimeouts(), Set.of("BD"));
    }
    if (budget == null) {
      budget = new Budget(1.0, 400_000);
    }
  }

  public Duration timeoutOf(Stage stage) {
    Duration configured =
        analysis.stepTimeouts() == null
            ? null
            : analysis.stepTimeouts().get(stage.name().toLowerCase());
    return configured != null ? configured : Duration.ofSeconds(60);
  }
}
