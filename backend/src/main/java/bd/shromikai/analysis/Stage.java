package bd.shromikai.analysis;

/** Pipeline states (architecture §5.1). P5 implements up to {@code CLASSIFY}. */
public enum Stage {
  INTAKE,
  OCR,
  REDACT,
  CLASSIFY,
  RETRIEVE,
  RULES,
  RISK,
  VERIFY,
  CORRECT,
  COMPOSE,
  COMPLETE
}
