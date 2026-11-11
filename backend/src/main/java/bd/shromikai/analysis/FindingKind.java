package bd.shromikai.analysis;

/** What kind of risk a finding is (api.md §6.1); a policy deviation comes from a playbook (P7). */
public enum FindingKind {
  CONFLICT,
  OMISSION,
  AMBIGUITY,
  INTERACTION,
  POLICY_DEVIATION
}
