package bd.shromikai.auth.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Data access for {@code refresh_token}. Only SHA-256 digests are stored (FR-05). */
@Repository
public class RefreshTokenRepository {

  /** A stored refresh token. */
  public record StoredToken(
      UUID id, UUID userId, UUID familyId, Instant expiresAt, @Nullable Instant revokedAt) {}

  private final JdbcClient jdbc;

  RefreshTokenRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public UUID insert(UUID userId, UUID familyId, String sha256, Instant expiresAt) {
    return jdbc.sql(
            """
            insert into refresh_token (user_id, family_id, token_sha256, expires_at)
            values (:user, :family, :hash, :expires) returning id
            """)
        .param("user", userId)
        .param("family", familyId)
        .param("hash", sha256)
        .param("expires", UserRepository.utc(expiresAt))
        .query(UUID.class)
        .single();
  }

  public Optional<StoredToken> find(String sha256) {
    return jdbc.sql(
            "select id, user_id, family_id, expires_at, revoked_at from refresh_token"
                + " where token_sha256 = :hash")
        .param("hash", sha256)
        .query(
            (rs, n) -> {
              OffsetDateTime revoked = rs.getObject("revoked_at", OffsetDateTime.class);
              return new StoredToken(
                  rs.getObject("id", UUID.class),
                  rs.getObject("user_id", UUID.class),
                  rs.getObject("family_id", UUID.class),
                  rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                  revoked == null ? null : revoked.toInstant());
            })
        .optional();
  }

  /** Revokes the token once; false means it was already revoked (a reuse). */
  public boolean markRotated(UUID id, Instant now) {
    return jdbc.sql(
                "update refresh_token set revoked_at = :now where id = :id and revoked_at is null")
            .param("now", UserRepository.utc(now))
            .param("id", id)
            .update()
        == 1;
  }

  public void setReplacedBy(UUID oldId, UUID newId) {
    jdbc.sql("update refresh_token set replaced_by = :new where id = :old")
        .param("new", newId)
        .param("old", oldId)
        .update();
  }

  public void revokeFamily(UUID familyId, Instant now) {
    jdbc.sql(
            "update refresh_token set revoked_at = :now where family_id = :family and revoked_at is null")
        .param("now", UserRepository.utc(now))
        .param("family", familyId)
        .update();
  }

  public void revokeAllForUser(UUID userId, Instant now) {
    jdbc.sql(
            "update refresh_token set revoked_at = :now where user_id = :user and revoked_at is null")
        .param("now", UserRepository.utc(now))
        .param("user", userId)
        .update();
  }
}
