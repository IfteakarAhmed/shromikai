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
