package bd.shromikai.auth.internal;

import bd.shromikai.auth.ApiKeyPrincipal;
import bd.shromikai.auth.ApiKeyView;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code api_key} table access; every organisation-scoped query filters by {@code org_id}. */
@Repository
public class ApiKeyRepository {

  private static final String COLUMNS =
      "id, org_id, prefix, name, scopes, created_at, last_used_at, revoked_at";

  private final JdbcClient jdbc;

  ApiKeyRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public ApiKeyView insert(
      UUID orgId, String prefix, String hash, String name, List<String> scopes, @Nullable UUID by) {
    return jdbc.sql(
            "insert into api_key (org_id, prefix, key_sha256, name, scopes, created_by)"
                + " values (:org, :prefix, :hash, :name, string_to_array(:scopes, ','), :by)"
                + " returning "
                + COLUMNS)
        .param("org", orgId)
        .param("prefix", prefix)
        .param("hash", hash)
        .param("name", name)
        .param("scopes", String.join(",", scopes))
        .param("by", by)
        .query((rs, n) -> view(rs))
        .single();
  }

  public List<ApiKeyView> list(UUID orgId) {
    return jdbc.sql("select " + COLUMNS + " from api_key where org_id = :org order by created_at")
        .param("org", orgId)
        .query((rs, n) -> view(rs))
        .list();
  }

  /** Revokes a key of the organisation; false when there is none (or it is another one's). */
  public boolean revoke(UUID orgId, UUID id) {
    return jdbc.sql(
                "update api_key set revoked_at = coalesce(revoked_at, now())"
                    + " where id = :id and org_id = :org")
            .param("id", id)
            .param("org", orgId)
            .update()
        > 0;
  }

  /** The principal of an active key with this hash. */
  public Optional<ApiKeyPrincipal> activeByHash(String hash) {
    return jdbc.sql(
            "select id, org_id, name, scopes from api_key"
                + " where key_sha256 = :hash and revoked_at is null")
        .param("hash", hash)
        .query(
            (rs, n) ->
                new ApiKeyPrincipal(
                    rs.getObject("id", UUID.class),
                    rs.getObject("org_id", UUID.class),
                    rs.getString("name"),
                    Set.of(strings(rs.getArray("scopes")))))
        .optional();
  }

  public void touch(UUID id) {
    jdbc.sql("update api_key set last_used_at = now() where id = :id").param("id", id).update();
  }

  private static ApiKeyView view(java.sql.ResultSet rs) throws SQLException {
    Timestamp used = rs.getTimestamp("last_used_at");
    Timestamp revoked = rs.getTimestamp("revoked_at");
    return new ApiKeyView(
        rs.getObject("id", UUID.class),
        rs.getObject("org_id", UUID.class),
        rs.getString("prefix"),
        rs.getString("name"),
        Arrays.asList(strings(rs.getArray("scopes"))),
        rs.getTimestamp("created_at").toInstant(),
        used == null ? null : used.toInstant(),
        revoked == null ? null : revoked.toInstant());
  }

  private static String[] strings(Array array) throws SQLException {
    return (String[]) array.getArray();
  }
}
