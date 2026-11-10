package bd.shromikai.shared.ai.fake;

/** Failure modes {@link FakeChatPort} can simulate per prompt ID (test-strategy.md §6.1). */
public enum FailureMode {
  TIMEOUT,
  RATE_LIMITED,
  SERVER_ERROR,
  REFUSAL,
  /** Returns JSON that fails the prompt's output schema on every call. */
  SCHEMA_INVALID,
  /** Returns schema-invalid JSON once, then the normal response (repair path). */
  INVALID_THEN_VALID
}
