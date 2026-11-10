package bd.shromikai.auth.internal;

import bd.shromikai.auth.OrgView;
import bd.shromikai.auth.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Data access for {@code app_user} and {@code organisation} through {@code JdbcClient}. */
@Repository
public class UserRepository {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final String COLUMNS =
      "id, org_id, email, password_hash, role, locale, profile::text as profile, is_demo,"
          + " failed_logins, locked_until, disabled_at";

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  UserRepository(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Optional<UserRecord> findByEmail(String email) {
    return jdbc.sql("select " + COLUMNS + " from app_user where email = :email")
        .param("email", email)
        .query(this::map)
        .optional();
  }

  public Optional<UserRecord> findById(UUID id) {
    return jdbc.sql("select " + COLUMNS + " from app_user where id = :id")
        .param("id", id)
        .query(this::map)
        .optional();
  }

  /** Counts one failed login and locks the account when {@code maxFailures} is reached. */
  public int recordFailure(UUID id, int maxFailures, Instant lockedUntil) {
    return jdbc.sql(
            """
            update app_user
               set failed_logins = failed_logins + 1,
                   locked_until = case when failed_logins + 1 >= :max then :until else locked_until end
             where id = :id
            returning failed_logins
            """)
        .param("max", maxFailures)
        .param("until", utc(lockedUntil))
        .param("id", id)
        .query(Integer.class)
        .single();
  }

  public void clearFailures(UUID id) {
    jdbc.sql("update app_user set failed_logins = 0, locked_until = null where id = :id")
        .param("id", id)
        .update();
  }

  public void updatePasswordHash(UUID id, String hash) {
    jdbc.sql("update app_user set password_hash = :hash where id = :id")
        .param("hash", hash)
        .param("id", id)
        .update();
  }

  /** Creates or refreshes a demo account; resets its password hash and lock state. */
  public void upsertDemo(String email, String hash, Role role, @Nullable UUID orgId) {
    jdbc.sql(
            """
            insert into app_user (email, password_hash, role, org_id, is_demo)
            values (:email, :hash, :role, cast(:org as uuid), true)
            on conflict (email) do update
               set password_hash = excluded.password_hash, role = excluded.role,
                   org_id = excluded.org_id, is_demo = true,
                   failed_logins = 0, locked_until = null, disabled_at = null
            """)
        .param("email", email)
        .param("hash", hash)
        .param("role", role.name())
        .param("org", orgId)
        .update();
  }

  public Optional<OrgView> findOrganisation(UUID id) {
    return jdbc.sql(
            "select id, name, kind, plan from organisation where id = :id and deleted_at is null")
        .param("id", id)
        .query(
            (rs, n) ->
                new OrgView(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("kind"),
                    rs.getString("plan")))
        .optional();
  }

  /** The demo organisation, created on first use. */
  public UUID demoOrganisation(String name) {
    return jdbc.sql("select id from organisation where name = :name and deleted_at is null")
        .param("name", name)
        .query(UUID.class)
        .optional()
        .orElseGet(
            () ->
                jdbc.sql(
                        "insert into organisation (name, kind, plan) values (:name, 'HR', 'TEAM')"
                            + " returning id")
                    .param("name", name)
                    .query(UUID.class)
                    .single());
  }

  private UserRecord map(ResultSet rs, int row) throws SQLException {
    OffsetDateTime locked = rs.getObject("locked_until", OffsetDateTime.class);
    OffsetDateTime disabled = rs.getObject("disabled_at", OffsetDateTime.class);
    return new UserRecord(
        rs.getObject("id", UUID.class),
        rs.getObject("org_id", UUID.class),
        rs.getString("email"),
        rs.getString("password_hash"),
        Role.valueOf(rs.getString("role")),
        rs.getString("locale"),
        profile(rs.getString("profile")),
        rs.getBoolean("is_demo"),
        rs.getInt("failed_logins"),
        locked == null ? null : locked.toInstant(),
        disabled == null ? null : disabled.toInstant());
  }

  private Map<String, Object> profile(String json) {
    try {
      return mapper.readValue(json, MAP);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }

  static OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
