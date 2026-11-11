package bd.shromikai.analysis;

/**
 * The finding-level verification status (architecture §7.4); {@code POLICY_ONLY} marks a playbook
 * finding (company policy, never law).
 */
public enum FindingVerification {
  VERIFIED,
  UNCERTAIN,
  POLICY_ONLY
}
