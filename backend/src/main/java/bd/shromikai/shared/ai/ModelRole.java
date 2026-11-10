package bd.shromikai.shared.ai;

/** Model roles of the prompt standard (AGENTS.md §5.2, ADR-0005). */
public enum ModelRole {
  LOCAL_PII,
  FAST,
  REASONING,
  VERIFIER,
  VERIFIER_FALLBACK,
  JUDGE,
  EMBED
}
