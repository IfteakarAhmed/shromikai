package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/** The provider stopped with a refusal ({@code stop_reason: refusal} for Claude). */
public final class ModelRefusedException extends ModelException {

  public ModelRefusedException(String reasonCode) {
    super(ProblemType.MODEL_REFUSED, reasonCode);
  }
}
