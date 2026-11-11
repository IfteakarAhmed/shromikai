package bd.shromikai.law.internal;

import org.jspecify.annotations.Nullable;

/** A page could not be fetched politely: refused by robots.txt, an HTTP error or no response. */
final class FetchException extends RuntimeException {

  enum Reason {
    ROBOTS_DISALLOWED,
    ROBOTS_UNAVAILABLE,
    HTTP_ERROR,
    UNREACHABLE,
    NOT_CACHED,
    REDIRECT_OFF_HOST,
    BODY_TOO_LARGE
  }

  private final Reason reason;
  private final int status;

  FetchException(Reason reason, String message, int status, @Nullable Throwable cause) {
    super(message, cause);
    this.reason = reason;
    this.status = status;
  }

  Reason reason() {
    return reason;
  }

  int status() {
    return status;
  }
}
