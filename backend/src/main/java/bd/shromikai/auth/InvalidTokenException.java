package bd.shromikai.auth;

/** The access token is missing a claim, expired, or not signed by this deployment. */
public final class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String reason) {
    super(reason);
  }

  public InvalidTokenException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
