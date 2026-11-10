package bd.shromikai.shared.problem;

import org.jspecify.annotations.Nullable;

/**
 * Raised by any module to return a problem document. {@code detail} must never contain contract
 * text or PII; it is shown to the user and may be logged.
 */
public class ProblemException extends RuntimeException {

  private final ProblemType type;
  private final @Nullable Integer retryAfterSeconds;

  public ProblemException(ProblemType type) {
    this(type, null, null);
  }

  public ProblemException(ProblemType type, @Nullable String detail) {
    this(type, detail, null);
  }

  public ProblemException(
      ProblemType type, @Nullable String detail, @Nullable Integer retryAfterSeconds) {
    super(detail == null ? type.code() : detail);
    this.type = type;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public ProblemType type() {
    return type;
  }

  public @Nullable String detail() {
    return getMessage().equals(type.code()) ? null : getMessage();
  }

  public @Nullable Integer retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
