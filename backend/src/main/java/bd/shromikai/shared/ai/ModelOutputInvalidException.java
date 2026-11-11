package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/**
 * The provider returned output that is not JSON or does not match the schema. It carries the tokens
 * the failed attempt used, so the cost ledger and the job budget stay exact (FR-48).
 */
public final class ModelOutputInvalidException extends ModelException {

  /** Reason code of a call that stopped at its output cap; repeating it would stop again. */
  public static final String MAX_TOKENS = "max-tokens";

  private final TokenUsage usage;

  public ModelOutputInvalidException(String reasonCode) {
    this(reasonCode, TokenUsage.NONE);
  }

  public ModelOutputInvalidException(String reasonCode, TokenUsage usage) {
    super(ProblemType.MODEL_OUTPUT_INVALID, reasonCode);
    this.usage = usage;
  }

  /** Tokens used by the attempts that produced no valid output. */
  public TokenUsage usage() {
    return usage;
  }

  /** True when the output was cut off at {@code max_output_tokens}. */
  public boolean stoppedAtCap() {
    return reasonCode().endsWith(MAX_TOKENS);
  }
}
