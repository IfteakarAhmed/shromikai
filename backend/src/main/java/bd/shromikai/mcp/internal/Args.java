package bd.shromikai.mcp.internal;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.util.Map;
import java.util.Set;

/**
 * Reads and checks tool arguments against the limits of api.md §9. A violation is a {@code
 * validation-failed} problem that names the argument, never its value (the value may hold personal
 * data).
 */
final class Args {

  private final Map<String, Object> values;

  Args(Map<String, Object> values) {
    this.values = values == null ? Map.of() : values;
  }

  String text(String name, int min, int max) {
    Object value = values.get(name);
    if (!(value instanceof String text) || text.length() < min || text.length() > max) {
      throw invalid(name + " must be text of " + min + " to " + max + " characters");
    }
    return text;
  }

  /**
   * A section number: text of 1 to 32 characters, or a whole number from 0 to 999,999 read as its
   * text ("100" for 100). Clients often send the number as a JSON number; a fraction, a negative
   * number, a larger number and anything else is refused.
   */
  String sectionNumber(String name) {
    Object value = values.get(name);
    if (value instanceof Number number) {
      double d = number.doubleValue();
      if (d == Math.rint(d) && d >= 0 && d <= 999_999) {
        return String.valueOf(number.longValue());
      }
      throw invalid(name + " must be text such as \"100\" or a whole number from 0 to 999999");
    }
    return text(name, 1, 32);
  }

  String text(String name, int min, int max, String fallback) {
    return values.get(name) == null ? fallback : text(name, min, max);
  }

  int integer(String name, int min, int max, int fallback) {
    Object value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof Number number)
        || number.doubleValue() != Math.rint(number.doubleValue())
        || number.intValue() < min
        || number.intValue() > max) {
      throw invalid(name + " must be a whole number from " + min + " to " + max);
    }
    return number.intValue();
  }

  String oneOf(String name, Set<String> allowed, String fallback) {
    Object value = values.get(name);
    if (value == null && fallback != null) {
      return fallback;
    }
    if (!(value instanceof String text) || !allowed.contains(text)) {
      throw invalid(name + " must be one of " + new java.util.TreeSet<>(allowed));
    }
    return text;
  }

  private static ProblemException invalid(String detail) {
    return new ProblemException(ProblemType.VALIDATION_FAILED, detail);
  }
}
