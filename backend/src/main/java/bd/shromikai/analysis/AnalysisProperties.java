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
      Duration hardTimeout,
      Map<String, Duration> stepTimeouts,
      Set<String> jurisdictions,
      Duration syncWait) {

    public Analysis(
        Duration hardTimeout, Map<String, Duration> stepTimeouts, Set<String> jurisdictions) {
      this(hardTimeout, stepTimeouts, jurisdictions, Duration.ofSeconds(45));
    }

    public Analysis {
      if (syncWait == null) {
        syncWait = Duration.ofSeconds(45);
      }
    }
  }

  public record Budget(double perAnalysisUsd, long perAnalysisTokens, double dailyUsd) {

    public Budget(double perAnalysisUsd, long perAnalysisTokens) {
      this(perAnalysisUsd, perAnalysisTokens, 50.0);
    }

    public long dailyUsdMicros() {
      return Math.round(dailyUsd * 1_000_000);
    }

    public long perAnalysisUsdMicros() {
      return Math.round(perAnalysisUsd * 1_000_000);
    }
  }

  public AnalysisProperties {
    if (analysis == null) {
      analysis = new Analysis(Duration.ofSeconds(150), Map.of(), Set.of("BD"));
    }
    if (analysis.jurisdictions() == null || analysis.jurisdictions().isEmpty()) {
      analysis =
          new Analysis(
              analysis.hardTimeout(), analysis.stepTimeouts(), Set.of("BD"), analysis.syncWait());
    }
    if (budget == null) {
      budget = new Budget(1.0, 400_000, 50.0);
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
