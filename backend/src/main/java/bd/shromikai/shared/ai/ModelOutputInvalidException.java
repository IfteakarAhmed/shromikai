package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/** The provider returned output that is not JSON or does not match the schema. */
public final class ModelOutputInvalidException extends ModelException {

  public ModelOutputInvalidException(String reasonCode) {
    super(ProblemType.MODEL_OUTPUT_INVALID, reasonCode);
  }
}
