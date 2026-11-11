package bd.shromikai.shared.problem;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Raised by any module to return a problem document. {@code detail} must never contain contract
 * text or PII; it is shown to the user and may be logged.
 */
public class ProblemException extends RuntimeException {

  private final ProblemType type;
  private final @Nullable Integer retryAfterSeconds;
  private final List<Integer> pages;

  public ProblemException(ProblemType type) {
    this(type, null, null);
  }

  public ProblemException(ProblemType type, @Nullable String detail) {
    this(type, detail, null);
  }

  public ProblemException(
      ProblemType type, @Nullable String detail, @Nullable Integer retryAfterSeconds) {
    this(type, detail, retryAfterSeconds, List.of());
  }

  /** A problem that names the document pages it is about ({@code unreadable-document}, api.md). */
  public ProblemException(
      ProblemType type,
      @Nullable String detail,
      @Nullable Integer retryAfterSeconds,
      List<Integer> pages) {
    super(detail == null ? type.code() : detail);
    this.type = type;
    this.retryAfterSeconds = retryAfterSeconds;
    this.pages = List.copyOf(pages);
  }

  /** One-based page numbers the problem refers to; empty for most problems. */
  public List<Integer> pages() {
    return pages;
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
