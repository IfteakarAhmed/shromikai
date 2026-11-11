package bd.shromikai.verify;

/** There is no WEAK status (ADR-0002). */
public enum VerificationStatus {
  VERIFIED,
  UNCERTAIN,
  REJECTED
}
