package bd.shromikai.trace;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One content-free audit record. {@code detail} may only hold the keys of {@code
 * schemas/audit/audit-detail.v1.schema.json}: never tokens, passwords, e-mail addresses or contract
 * text (R-02, FR-10).
 */
public record AuditEvent(
    @Nullable UUID actorId,
    ActorKind actorKind,
    AuditAction action,
    @Nullable String targetType,
    @Nullable String targetId,
    Map<String, Object> detail) {

  public static AuditEvent of(
      @Nullable UUID actorId, ActorKind actorKind, AuditAction action, Map<String, Object> detail) {
    return new AuditEvent(actorId, actorKind, action, null, null, detail);
  }

  public AuditEvent withTarget(String type, @Nullable String id) {
    return new AuditEvent(actorId, actorKind, action, type, id, detail);
  }
}
