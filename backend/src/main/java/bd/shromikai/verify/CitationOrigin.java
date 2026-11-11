package bd.shromikai.verify;

/** Who produced a claimed citation (architecture §7.1). */
public enum CitationOrigin {
  LLM,
  RULE,
  MAPPING,
  REVIEWER,
  EXTERNAL,
  BASELINE
}
