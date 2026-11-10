package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/** The call exceeded the prompt's {@code timeout_seconds}. */
public final class ModelTimeoutException extends ModelException {

  public ModelTimeoutException(String reasonCode) {
    super(ProblemType.ANALYSIS_TIMEOUT, reasonCode);
  }
}
