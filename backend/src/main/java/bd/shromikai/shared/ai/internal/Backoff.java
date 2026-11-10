package bd.shromikai.shared.ai.internal;

import bd.shromikai.shared.ai.UpstreamUnavailableException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * At most 2 retries with exponential backoff starting at 500 ms, only for retryable failures: HTTP
 * 408, 429, 5xx and connection errors (AGENTS.md §6).
 */
public final class Backoff {

  public static final int MAX_RETRIES = 2;

  private final Duration initialDelay;
  private final Sleeper sleeper;

  public Backoff() {
    this(Duration.ofMillis(500), Thread::sleep);
  }

  public Backoff(Duration initialDelay, Sleeper sleeper) {
    this.initialDelay = initialDelay;
    this.sleeper = sleeper;
  }

  public <T> T call(Supplier<T> action) {
    Duration delay = initialDelay;
    for (int attempt = 0; ; attempt++) {
      try {
        return action.get();
      } catch (UpstreamUnavailableException e) {
        if (attempt >= MAX_RETRIES || !retryable(e.httpStatus())) {
          throw e;
        }
        sleep(delay);
        delay = delay.multipliedBy(2);
      }
    }
  }

  /** Connection errors report status 0. */
  public static boolean retryable(int httpStatus) {
    return httpStatus == 0 || httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
  }

  private void sleep(Duration delay) {
    try {
      sleeper.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpstreamUnavailableException("interrupted", 0, null);
    }
  }

  /** Sleep hook so tests do not wait. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
