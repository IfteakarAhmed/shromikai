package bd.shromikai.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Writes one row of {@code analysis_trace} per state transition (architecture §5.1, §11.2, FR-48).
 * The detail holds section keys, statuses, scores, reason codes, flags and counts only; the table's
 * size check and the callers' construction keep contract text out (R-02).
 */
@Service
public class AnalysisTraceStore {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  public AnalysisTraceStore(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void append(
      UUID analysisId,
      String step,
      Instant at,
      long durationMs,
      String outcome,
      @Nullable String promptId,
      @Nullable Integer promptVersion,
      @Nullable String modelId,
      @Nullable Long tokensIn,
      @Nullable Long tokensOut,
      @Nullable Long costUsdMicros,
      Map<String, Object> detail) {
    String json;
    try {
      json = mapper.writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Trace detail is not JSON", e);
    }
    jdbc.sql(
            """
            insert into analysis_trace (analysis_id, step, detail, created_at, prompt_id,
                                        prompt_version, model_id, duration_ms, tokens_in,
                                        tokens_out, cost_usd_micros, outcome)
            values (:analysis, :step, cast(:detail as jsonb), :at, :promptId, :promptVersion,
                    :modelId, :duration, :tokensIn, :tokensOut, :cost, :outcome)
            """)
        .param("analysis", analysisId)
        .param("step", step)
        .param("detail", json)
        .param("at", java.sql.Timestamp.from(at))
        .param("promptId", promptId)
        .param("promptVersion", promptVersion)
        .param("modelId", modelId)
        .param("duration", durationMs)
        .param("tokensIn", tokensIn)
        .param("tokensOut", tokensOut)
        .param("cost", costUsdMicros)
        .param("outcome", outcome)
        .update();
  }
}
