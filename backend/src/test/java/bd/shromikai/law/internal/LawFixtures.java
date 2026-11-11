package bd.shromikai.law.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved pages of the official portal under {@code src/test/resources/fixtures/law/}. */
final class LawFixtures {

  static final String BASE_URL = "http://bdlaws.minlaw.gov.bd";

  private LawFixtures() {}

  static String read(String act, String page) {
    try {
      return Files.readString(Path.of("src/test/resources/fixtures/law", act, page));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static ParsedAct parse(String act) {
    return new ActParser(BASE_URL).parse(read(act, "index.html"), read(act, "print.html"));
  }
}
