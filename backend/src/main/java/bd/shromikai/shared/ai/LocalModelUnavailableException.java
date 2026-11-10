package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/** Ollama cannot be reached; the analysis fails closed and nothing is sent (R-03, FR-31). */
public final class LocalModelUnavailableException extends ModelException {

  public LocalModelUnavailableException(String reasonCode) {
    super(ProblemType.LOCAL_MODEL_UNAVAILABLE, reasonCode);
  }
}
