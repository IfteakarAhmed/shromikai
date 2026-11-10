package bd.shromikai.trace;

/** Who caused an audit event; matches the {@code actor_kind} check in {@code audit_event}. */
public enum ActorKind {
  USER,
  GUEST,
  API_KEY,
  SYSTEM
}
