package bd.shromikai.analysis;

/** What kind of risk a finding is (api.md §6.1); policy deviations arrive with playbooks in P7. */
public enum FindingKind {
  CONFLICT,
  OMISSION,
  AMBIGUITY,
  INTERACTION
}
