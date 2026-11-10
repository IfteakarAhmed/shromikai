package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;
import org.jspecify.annotations.Nullable;

/** A provider failed (HTTP 408, 429, 5xx, connection error) or is not configured. */
public final class UpstreamUnavailableException extends ModelException {

  /** Status used when the provider has no API key or model; never retried. */
  public static final int NOT_CONFIGURED = -1;

  private final int httpStatus;

  public UpstreamUnavailableException(
      String reasonCode, int httpStatus, @Nullable Integer retryAfterSeconds) {
    super(ProblemType.UPSTREAM_UNAVAILABLE, reasonCode, retryAfterSeconds);
    this.httpStatus = httpStatus;
  }

  /**
   * The provider's HTTP status; 0 for connection errors; {@value #NOT_CONFIGURED} if unconfigured.
   */
  public int httpStatus() {
    return httpStatus;
  }
}
