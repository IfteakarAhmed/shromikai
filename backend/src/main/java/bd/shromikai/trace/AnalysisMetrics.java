package bd.shromikai.trace;

import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.LlmCall;
import bd.shromikai.shared.ai.LlmCallListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The Micrometer metrics of architecture §15 (FR-82) that the analysis and the model gateway feed:
 * stage latency, analyses by outcome, and tokens, cost and calls by model. Labels are codes, model
 * IDs and prompt IDs only; anything else is replaced by {@code other}, so no label can carry
 * content. Replayed (recorded) calls count their tokens but no cost, since nothing was spent.
 */
@Component
public class AnalysisMetrics implements LlmCallListener {

  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

  private final MeterRegistry meters;
  private final CostCalculator cost;

  public AnalysisMetrics(MeterRegistry meters, CostCalculator cost) {
    this.meters = meters;
    this.cost = cost;
    // Registered up front so the series exist from the first scrape.
    Counter.builder("shromikai_analyses_total")
        .tags(
            "status", "SUCCEEDED", "input_kind", "TEXT", "client_kind", "web", "error_code", "none")
        .register(meters);
  }

  static String safe(String value) {
    return value != null && SAFE.matcher(value).matches() ? value : "other";
  }

  /** One finished stage of an analysis. */
  public void stage(String stage, String inputKind, long durationMs) {
    Timer.builder("shromikai_analysis_duration_seconds")
        .tags("stage", safe(stage), "input_kind", safe(inputKind))
        .publishPercentileHistogram()
        .register(meters)
        .record(Duration.ofMillis(Math.max(0, durationMs)));
  }

  /** One finished analysis: SUCCEEDED or FAILED with its problem code. */
  public void analysis(String status, String inputKind, String clientKind, String errorCode) {
    meters
        .counter(
            "shromikai_analyses_total",
            "status",
            safe(status),
            "input_kind",
            safe(inputKind),
            "client_kind",
            safe(clientKind),
            "error_code",
            safe(errorCode))
        .increment();
  }

  @Override
  public void onCall(LlmCall call) {
    String role = safe(call.role().name());
    String model = safe(call.modelId());
    var usage = call.usage();
    meters
        .counter("shromikai_llm_tokens_total", "role", role, "model", model, "direction", "in")
        .increment(usage.inputTokens());
    meters
        .counter("shromikai_llm_tokens_total", "role", role, "model", model, "direction", "out")
        .increment(usage.outputTokens());
    meters
        .counter(
            "shromikai_llm_tokens_total", "role", role, "model", model, "direction", "cache_read")
        .increment(usage.cacheReadTokens());
    Counter spend = meters.counter("shromikai_llm_cost_usd_total", "role", role, "model", model);
    if (!call.recorded()) {
      spend.increment(cost.usdMicros(call.modelId(), usage) / 1_000_000.0);
    }
    meters
        .counter(
            "shromikai_llm_calls_total",
            "prompt_id",
            safe(call.promptId()),
            "prompt_version",
            String.valueOf(call.promptVersion()),
            "outcome",
            call.recorded() ? "recorded" : "ok")
        .increment();
  }
}
