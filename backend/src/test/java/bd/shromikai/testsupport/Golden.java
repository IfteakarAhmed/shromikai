package bd.shromikai.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Golden files (test-strategy.md §7): the expected text lives under {@code
 * src/test/resources/golden} and changes only when the build runs with {@code -PupdateGolden=true},
 * so the diff is reviewed like code.
 */
public final class Golden {

  private static final Path ROOT = Path.of("src/test/resources/golden");

  private Golden() {}

  /** The expected content, or the actual one after writing it when updating. */
  public static String expect(String name, String actual) {
    Path file = ROOT.resolve(name);
    if (Boolean.getBoolean("updateGolden")) {
      try {
        Files.createDirectories(file.getParent());
        Files.writeString(file, actual, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return actual;
    }
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(
          "No golden file " + file + ": run ./gradlew test -PupdateGolden=true and review the diff",
          e);
    }
  }
}
