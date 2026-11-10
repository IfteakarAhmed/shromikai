package bd.shromikai.shared.ai;

import bd.shromikai.shared.problem.ProblemType;

/**
 * The provider rejected the request with a 4xx other than 408 or 429 (bad request, bad key, unknown
 * model). That is our fault, not the provider's, so it maps to {@code internal-error} and is never
 * retried (NFR-04).
 */
public final class ModelRequestRejectedException extends ModelException {

  private final int httpStatus;

  public ModelRequestRejectedException(String reasonCode, int httpStatus) {
    super(ProblemType.INTERNAL_ERROR, reasonCode);
    this.httpStatus = httpStatus;
  }

  public int httpStatus() {
    return httpStatus;
  }

  /** True for 4xx statuses that are neither 408 nor 429. */
  public static boolean applies(int httpStatus) {
    return httpStatus >= 400 && httpStatus < 500 && httpStatus != 408 && httpStatus != 429;
  }
}
