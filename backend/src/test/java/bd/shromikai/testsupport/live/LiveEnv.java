package bd.shromikai.testsupport.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads {@code ../.env} for live tests. Values are used to configure the adapters and are never
 * printed or logged. Live tests also need an explicit opt-in through {@code -PliveOk=smoke} or
 * {@code -PliveOk=record}, so {@code ./gradlew liveTest} cannot spend money by accident.
 */
public final class LiveEnv {

  private static final Map<String, String> VALUES = load();

  private LiveEnv() {}

  private static Map<String, String> load() {
    Map<String, String> out = new HashMap<>();
    Path file = Path.of("..", ".env");
    if (Files.isRegularFile(file)) {
      try {
        for (String line : Files.readAllLines(file)) {
          String trimmed = line.strip();
          if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
            continue;
          }
          int eq = trimmed.indexOf('=');
          String value = trimmed.substring(eq + 1).strip();
          if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          }
          out.put(trimmed.substring(0, eq).strip(), value);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return out;
  }

  public static String get(String name) {
    String fromEnv = System.getenv(name);
    return fromEnv != null && !fromEnv.isBlank() ? fromEnv : VALUES.getOrDefault(name, "");
  }

  public static boolean has(String name) {
    return !get(name).isBlank();
  }

  /** Whether the run opted in to the given kind of live test ({@code smoke} or {@code record}). */
  public static boolean optedIn(String kind) {
    return kind.equals(System.getProperty("shromikai.live"));
  }
}
