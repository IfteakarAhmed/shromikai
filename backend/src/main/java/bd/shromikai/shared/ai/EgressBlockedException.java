package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/** The egress guard stopped a cloud call (R-03, FR-30). */
public final class EgressBlockedException extends ModelException {

  public EgressBlockedException(String reasonCode) {
    super(ProblemType.PRIVACY_GUARD_BLOCKED, reasonCode);
  }
}
