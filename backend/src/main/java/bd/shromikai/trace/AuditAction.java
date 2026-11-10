package bd.shromikai.trace;

/**
 * Audited actions; matches the {@code action} check in {@code audit_event} (architecture §11.2).
 */
public enum AuditAction {
  LOGIN_SUCCESS,
  LOGIN_FAILURE,
  DEMO_LOGIN,
  ACCESS_DENIED,
  PRIVACY_BLOCK,
  REVIEW_DECISION,
  ADMIN_ACTION,
  API_KEY_CREATED,
  API_KEY_REVOKED
}
