package bd.shromikai.privacy;

import bd.shromikai.shared.ai.EgressContext;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The job's placeholder map: original value to {@code [TYPE_n]} (architecture §10.4). It lives only
 * in the memory of one analysis job. It is deliberately not {@code Serializable}, its {@link
 * #toString()} is masked, and {@link #clear()} drops every value when the job ends (R-02, FR-29).
 */
public final class PlaceholderMap implements EgressContext {

  private final Map<PiiType, Map<String, String>> byType = new EnumMap<>(PiiType.class);
  private final Map<String, String> placeholderToOriginal = new LinkedHashMap<>();
  private final @Nullable String analysisId;

  public PlaceholderMap() {
    this(null);
  }

  public PlaceholderMap(@Nullable String analysisId) {
    this.analysisId = analysisId;
  }

  /** The placeholder of a value, creating the next {@code [TYPE_n]} the first time it is seen. */
  public synchronized String placeholderFor(PiiType type, String original) {
    Map<String, String> values = byType.computeIfAbsent(type, t -> new LinkedHashMap<>());
    String existing = values.get(original);
    if (existing != null) {
      return existing;
    }
    String placeholder = "[" + type.stem() + "_" + (values.size() + 1) + "]";
    values.put(original, placeholder);
    placeholderToOriginal.put(placeholder, original);
    return placeholder;
  }

  /** The original behind a placeholder, or {@code null} if unknown. */
  public synchronized @Nullable String original(String placeholder) {
    return placeholderToOriginal.get(placeholder);
  }

  /** Replaces every known placeholder in the text by its original value. */
  public synchronized String restore(String redacted) {
    if (placeholderToOriginal.isEmpty() || redacted.indexOf('[') < 0) {
      return redacted;
    }
    StringBuilder out = new StringBuilder(redacted.length());
    int i = 0;
    while (i < redacted.length()) {
      char c = redacted.charAt(i);
      int close = c == '[' ? redacted.indexOf(']', i) : -1;
      if (close > 0) {
        String original = placeholderToOriginal.get(redacted.substring(i, close + 1));
        if (original != null) {
          out.append(original);
          i = close + 1;
          continue;
        }
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  @Override
  public synchronized Set<String> protectedValues() {
    Set<String> values = new TreeSet<>();
    byType.values().forEach(m -> values.addAll(m.keySet()));
    return values;
  }

  @Override
  public @Nullable String analysisId() {
    return analysisId;
  }

  public synchronized int size() {
    return placeholderToOriginal.size();
  }

  /** Forgets every value; called when the job is purged. */
  public synchronized void clear() {
    byType.clear();
    placeholderToOriginal.clear();
  }

  @Override
  public synchronized String toString() {
    return "PlaceholderMap[masked, entries=" + placeholderToOriginal.size() + "]";
  }
}
