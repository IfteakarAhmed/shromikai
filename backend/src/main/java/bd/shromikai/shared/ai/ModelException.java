package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import org.jspecify.annotations.Nullable;

/**
 * Base type for model-call failures. The reason code (for example {@code gemini-http-503}) is for
 * logs and traces only; it never becomes the client-facing {@code detail} (NFR-04).
 */
public class ModelException extends ProblemException {

  private final String reasonCode;

  public ModelException(ProblemType type, String reasonCode) {
    this(type, reasonCode, null);
  }

  public ModelException(ProblemType type, String reasonCode, @Nullable Integer retryAfterSeconds) {
    super(type, null, retryAfterSeconds);
    this.reasonCode = reasonCode;
  }

  /** Content-free machine reason for logs and traces. */
  public String reasonCode() {
    return reasonCode;
  }

  @Override
  public String getMessage() {
    return reasonCode;
  }

  @Override
  public @Nullable String detail() {
    return null;
  }
}
