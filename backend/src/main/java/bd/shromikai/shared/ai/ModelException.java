package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import org.jspecify.annotations.Nullable;

/** Base type for model-call failures; messages hold reason codes only, never content. */
public class ModelException extends ProblemException {

  public ModelException(ProblemType type, String reasonCode) {
    super(type, reasonCode);
  }

  public ModelException(ProblemType type, String reasonCode, @Nullable Integer retryAfterSeconds) {
    super(type, reasonCode, retryAfterSeconds);
  }
}
