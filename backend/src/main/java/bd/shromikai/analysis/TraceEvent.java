package bd.shromikai.analysis;

import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One content-free trace event (architecture §15): stage, timing, outcome and a detail map of
 * section keys, flags, reason codes and counts only. No text, no values.
 */
public record TraceEvent(
    Stage stage,
    Instant at,
    long durationMs,
    String outcome,
    @Nullable String promptId,
    @Nullable Integer promptVersion,
    @Nullable String modelId,
    Map<String, Object> detail,
    @Nullable Long tokensIn,
    @Nullable Long tokensOut,
    @Nullable Long costUsdMicros) {

  public static final String OK = "OK";
  public static final String FAILED = "FAILED";

  public TraceEvent {
    detail = Map.copyOf(detail);
  }

  /** An event without model usage (a local step). */
  public TraceEvent(
      Stage stage,
      Instant at,
      long durationMs,
      String outcome,
      @Nullable String promptId,
      @Nullable Integer promptVersion,
      @Nullable String modelId,
      Map<String, Object> detail) {
    this(
        stage, at, durationMs, outcome, promptId, promptVersion, modelId, detail, null, null, null);
  }
}
