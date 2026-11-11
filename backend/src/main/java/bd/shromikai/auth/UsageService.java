package bd.shromikai.auth;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Writes {@code usage_event} rows (FR-65): kind, units and cost, never content (R-02). */
@Service
public class UsageService {

  /** Mirrors the {@code kind} check of the table. */
  public enum Kind {
    ANALYSIS,
    COMPARE,
    MEMO_RENDER,
    API_CALL,
    MCP_CALL
  }

  private final JdbcClient jdbc;

  UsageService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void record(
      @Nullable UUID orgId,
      @Nullable UUID apiKeyId,
      @Nullable UUID userId,
      Kind kind,
      int units,
      long costUsdMicros) {
    jdbc.sql(
            "insert into usage_event (org_id, api_key_id, user_id, kind, units, cost_usd_micros)"
                + " values (:org, :key, :user, :kind, :units, :cost)")
        .param("org", orgId)
        .param("key", apiKeyId)
        .param("user", userId)
        .param("kind", kind.name())
        .param("units", units)
        .param("cost", costUsdMicros)
        .update();
  }
}
