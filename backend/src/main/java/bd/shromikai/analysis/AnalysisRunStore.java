package bd.shromikai.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Metadata of an analysis in {@code analysis_run} (architecture §11.2): never contract text. */
@Repository
public class AnalysisRunStore {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  AnalysisRunStore(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  void start(
      AnalysisJob job,
      AnalysisRequest request,
      AnalysisSubject subject,
      String pipelineVersion,
      @Nullable String corpusRelease) {
    jdbc.sql(
            """
            insert into analysis_run (id, subject_kind, subject_hash, org_id, client_kind, side,
                                      output_language, input_kind, page_count, detected_language,
                                      status, corpus_release_id, pipeline_version, created_at)
            values (:id, :kind, :hash, :org, :client, :side, :lang, :input, :pages, :detected,
                    'RUNNING', (select id from corpus_release where label = :release), :pipeline,
                    :created)
            """)
        .param("id", job.id())
        .param("kind", subject.kind())
        .param("hash", subject.hash())
        .param("org", subject.orgId())
        .param("client", subject.clientKind())
        .param("side", request.side().name())
        .param("lang", request.outputLanguage())
        .param("input", job.inputKind())
        .param("pages", job.pageCount())
        .param("detected", job.language())
        .param("release", corpusRelease)
        .param("pipeline", pipelineVersion)
        .param("created", Timestamp.from(job.createdAt()))
        .update();
  }

  void succeeded(
      AnalysisJob job,
      int findings,
      int verifiedCitations,
      int uncertain,
      Map<String, Integer> promptVersions,
      Map<String, String> models,
      long costMicros,
      long tokensIn,
      long tokensOut,
      long cacheReadTokens,
      long cacheWriteTokens,
      long durationMs,
      Instant completedAt) {
    jdbc.sql(
            """
            update analysis_run
               set status = 'SUCCEEDED', finding_count = :findings,
                   verified_citation_count = :citations, uncertain_finding_count = :uncertain,
                   prompt_versions = cast(:prompts as jsonb), model_ids = cast(:models as jsonb),
                   cost_usd_micros = :cost, tokens_in = :in, tokens_out = :out,
                   cache_read_tokens = :cacheRead, cache_write_tokens = :cacheWrite,
                   duration_ms = :duration, completed_at = :completed
             where id = :id
            """)
        .param("id", job.id())
        .param("findings", findings)
        .param("citations", verifiedCitations)
        .param("uncertain", uncertain)
        .param("prompts", json(promptVersions))
        .param("models", json(models))
        .param("cost", costMicros)
        .param("in", tokensIn)
        .param("out", tokensOut)
        .param("cacheRead", cacheReadTokens)
        .param("cacheWrite", cacheWriteTokens)
        .param("duration", durationMs)
        .param("completed", Timestamp.from(completedAt))
        .update();
  }

  void failed(
      AnalysisJob job, String errorCode, long costMicros, long durationMs, Instant completedAt) {
    jdbc.sql(
            """
            update analysis_run
               set status = 'FAILED', error_code = :code, cost_usd_micros = :cost,
                   duration_ms = :duration, completed_at = :completed
             where id = :id
            """)
        .param("id", job.id())
        .param("code", errorCode)
        .param("cost", costMicros)
        .param("duration", durationMs)
        .param("completed", Timestamp.from(completedAt))
        .update();
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /** What the trace endpoint needs about a run: its owner key, creation time and totals. */
  public record RunInfo(
      String ownerKey,
      Instant createdAt,
      long tokensIn,
      long tokensOut,
      long cacheReadTokens,
      long cacheWriteTokens,
      long costUsdMicros) {}

  public java.util.Optional<RunInfo> info(java.util.UUID id) {
    return jdbc.sql(
            """
            select subject_kind, subject_hash, created_at, tokens_in, tokens_out,
                   cache_read_tokens, cache_write_tokens, cost_usd_micros
              from analysis_run where id = :id
            """)
        .param("id", id)
        .query(
            (rs, n) ->
                new RunInfo(
                    rs.getString("subject_kind") + ":" + rs.getString("subject_hash"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getLong("tokens_in"),
                    rs.getLong("tokens_out"),
                    rs.getLong("cache_read_tokens"),
                    rs.getLong("cache_write_tokens"),
                    rs.getLong("cost_usd_micros")))
        .optional();
  }
}
