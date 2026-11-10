package bd.shromikai.shared.ai.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;

/** Reads a provider's {@code Retry-After} header (delay-seconds or HTTP-date, RFC 9110 §10.2.3). */
public final class RetryAfter {

  private RetryAfter() {}

  /** Seconds to pass on to the client for 429 and 503 responses; {@code null} otherwise. */
  public static @Nullable Integer forStatus(int httpStatus, @Nullable String header, Instant now) {
    return httpStatus == 429 || httpStatus == 503 ? seconds(header, now) : null;
  }

  public static @Nullable Integer seconds(@Nullable String header, Instant now) {
    if (header == null || header.isBlank()) {
      return null;
    }
    String value = header.trim();
    if (value.chars().allMatch(Character::isDigit)) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        return Integer.MAX_VALUE;
      }
    }
    try {
      Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
      long seconds = Duration.between(now, at).toSeconds();
      return (int) Math.max(0, Math.min(seconds, Integer.MAX_VALUE));
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
