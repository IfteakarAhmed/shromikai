package bd.shromikai.verify;

import org.jspecify.annotations.Nullable;

/**
 * A typed clause fact as a review item shows it: parameter, number and unit only (no clause text).
 */
public record FactView(String parameter, @Nullable Double value, String unit) {

  /** {@code NOTICE_BY_EMPLOYER=7 DAYS}. */
  public String display() {
    String number =
        value == null
            ? "stated without a value"
            : value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    return parameter + "=" + number + " " + unit;
  }
}
