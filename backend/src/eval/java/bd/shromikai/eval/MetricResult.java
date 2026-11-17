package bd.shromikai.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One metric value as reported (evaluation.md §12): the value with its count and n, the Wilson
 * interval for proportions, the target and whether it was met, and on what data it rests. A metric
 * that could not be measured says why: {@code NOT_AVAILABLE} (the data or reviewer does not exist),
 * {@code NOT_DONE} (cut from scope) or {@code NOT_RUN} (budget or time).
 *
 * @param basis what the number rests on, for example "3 contracts, worker side, draft labels"
 * @param extra further numbers (percentiles, misses, breakdowns), content-free
 */
public record MetricResult(
    String id,
    String name,
    Status status,
    @Nullable Double value,
    @Nullable Long count,
    @Nullable Long n,
    @Nullable List<Double> ci95,
    @Nullable String target,
    @Nullable Boolean met,
    String basis,
    @Nullable String note,
    Map<String, Object> extra) {

  public enum Status {
    MEASURED,
    NOT_AVAILABLE,
    NOT_DONE,
    NOT_RUN
  }

  public MetricResult {
    extra =
        extra == null
            ? Map.of()
            : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(extra));
  }

  private static MetricCatalog.Definition def(String id) {
    return MetricCatalog.get(id);
  }

  private static @Nullable Boolean met(String id, double value) {
    var test = def(id).met();
    return test == null || Double.isNaN(value) ? null : test.test(value);
  }

  /** {@code k} of {@code n}, with the Wilson interval. */
  public static MetricResult proportion(String id, long k, long n, String basis) {
    return proportion(id, k, n, basis, Map.of());
  }

  public static MetricResult proportion(
      String id, long k, long n, String basis, Map<String, Object> extra) {
    Double value = n == 0 ? null : (double) k / n;
    double[] ci = Stats.wilson(k, n);
    return new MetricResult(
        id,
        def(id).name(),
        Status.MEASURED,
        value,
        k,
        n,
        ci == null ? null : List.of(round(ci[0]), round(ci[1])),
        def(id).target(),
        value == null ? null : met(id, value),
        basis,
        null,
        extra);
  }

  /** A count over {@code n} observed items (for example canary hits over requests scanned). */
  public static MetricResult count(
      String id, long count, long n, String basis, Map<String, Object> extra) {
    return new MetricResult(
        id,
        def(id).name(),
        Status.MEASURED,
        (double) count,
        count,
        n,
        null,
        def(id).target(),
        met(id, count),
        basis,
        null,
        extra);
  }

  /** A plain number (a percentile, a mean) over {@code n} items. */
  public static MetricResult number(
      String id, double value, long n, String basis, Map<String, Object> extra) {
    return new MetricResult(
        id,
        def(id).name(),
        Status.MEASURED,
        Double.isNaN(value) ? null : value,
        null,
        n,
        null,
        def(id).target(),
        met(id, value),
        basis,
        null,
        extra);
  }

  private static MetricResult missing(String id, Status status, String reason) {
    return new MetricResult(
        id,
        def(id).name(),
        status,
        null,
        null,
        null,
        null,
        def(id).target(),
        null,
        "",
        reason,
        Map.of());
  }

  public static MetricResult notAvailable(String id, String reason) {
    return missing(id, Status.NOT_AVAILABLE, reason);
  }

  public static MetricResult notDone(String id, String reason) {
    return missing(id, Status.NOT_DONE, reason);
  }

  public static MetricResult notRun(String id, String reason) {
    return missing(id, Status.NOT_RUN, reason);
  }

  public MetricResult withNote(String newNote) {
    return new MetricResult(
        id, name, status, value, count, n, ci95, target, met, basis, newNote, extra);
  }

  public MetricResult withExtra(String key, Object v) {
    Map<String, Object> more = new LinkedHashMap<>(extra);
    more.put(key, v);
    return new MetricResult(
        id, name, status, value, count, n, ci95, target, met, basis, note, more);
  }

  /** As shown in the report: "0.920 (23 of 25)", "0 (of 13)", "not available: why". */
  public String display() {
    return switch (status) {
      case NOT_AVAILABLE -> "not available: " + note;
      case NOT_DONE -> "not done: " + note;
      case NOT_RUN -> "not run: " + note;
      case MEASURED -> {
        if (n != null && n == 0) {
          yield "n = 0";
        }
        if (count != null && ci95 != null) {
          yield String.format(Locale.ROOT, "%.3f (%d of %d)", value, count, n);
        }
        if (count != null) {
          yield String.format(Locale.ROOT, "%d (of %d)", count, n);
        }
        yield value == null ? "n = " + n : String.format(Locale.ROOT, "%.3f (n = %d)", value, n);
      }
    };
  }

  static double round(double v) {
    return Math.round(v * 10_000) / 10_000.0;
  }
}
