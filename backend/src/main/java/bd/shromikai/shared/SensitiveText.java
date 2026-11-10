package bd.shromikai.shared;

import java.util.Objects;

/**
 * Holds contract-derived text (R-02). {@link #toString()} never reveals the content, so the value
 * cannot leak through logging, exception messages or debugger-style string conversion.
 */
public final class SensitiveText {

  private final String value;

  private SensitiveText(String value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  public static SensitiveText of(String value) {
    return new SensitiveText(value);
  }

  /** Returns the raw text. Call only where the text is processed, never to log or persist it. */
  public String reveal() {
    return value;
  }

  public int length() {
    return value.length();
  }

  public boolean isEmpty() {
    return value.isEmpty();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SensitiveText that && value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "SensitiveText[masked, length=" + value.length() + "]";
  }
}
