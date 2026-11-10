package bd.shromikai.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fixtures carry provenance, valid recorded responses and no unlisted PII (R-02, NFR-05). */
@Tag("unit")
@Requirement("NFR-05")
class FixtureScannerTest {

  @TempDir Path root;
  Path fixtures;

  @BeforeEach
  void layout() throws IOException {
    fixtures = Files.createDirectories(root.resolve("fixtures"));
    Files.createDirectories(root.resolve("schemas/llm"));
    Files.copy(
        Path.of("src/test/resources/schemas/llm/xx-echo.v1.schema.json"),
        root.resolve("schemas/llm/xx-echo.v1.schema.json"));
    Files.writeString(
        fixtures.resolve("canaries.yml"),
        "values:\n  phones: ['01399999999']\n  emails: ['canary@example.invalid']\n");
  }

  private FixtureScanner scanner() {
    return new FixtureScanner(root, Path.of("src/main/resources"));
  }

  @Test
  void shouldPassTheRepositoryFixtures() throws IOException {
    assertThat(
            new FixtureScanner(Path.of("src/test/resources"), Path.of("src/main/resources")).scan())
        .isEmpty();
  }

  @Test
  void shouldAcceptCanaryValues_andRejectUnlistedPii() throws IOException {
    Files.createDirectories(fixtures.resolve("notes"));
    Files.writeString(
        fixtures.resolve("notes/ok.txt"), "call 01399999999 or canary@example.invalid");
    Files.writeString(fixtures.resolve("notes/bad.txt"), "call 01712345678");

    assertThat(scanner().scan())
        .containsExactly("notes/bad.txt: PII-like value not listed in canaries.yml");
  }

  @Test
  void shouldRequireProvenanceSidecar_forDocuments() throws IOException {
    Path documents = Files.createDirectories(fixtures.resolve("documents"));
    Files.writeString(documents.resolve("a.txt"), "Synthetic contract.");
    Files.writeString(documents.resolve("b.txt"), "Synthetic contract.");
    Files.writeString(
        documents.resolve("b.txt.meta.yml"),
        "provenance: synthetic\nsource: team\nlicence: CC0-1.0\n");
    Files.writeString(documents.resolve("c.txt"), "Real contract.");
    Files.writeString(documents.resolve("c.txt.meta.yml"), "provenance: real\nsource: x\n");

    assertThat(scanner().scan())
        .containsExactlyInAnyOrder(
            "documents/a.txt: missing sidecar a.txt.meta.yml",
            "documents/c.txt: provenance must be synthetic or public-template",
            "documents/c.txt: sidecar needs licence");
  }

  @Test
  void shouldRejectInvalidModelFixtures() throws IOException {
    Path dir = Files.createDirectories(fixtures.resolve("llm/xx-echo/v1"));
    Files.writeString(
        dir.resolve("case-1.json"),
        """
        {"fixtureVersion":1,"promptId":"xx-echo","promptVersion":1,"caseId":"case-1",
         "modelId":"m","requestSha256":"%s","provenance":"recorded-from-user","recordedAt":"2026-11-10",
         "response":{"echo":"canary@example.invalid"}}
        """
            .formatted("a".repeat(64)));
    Files.writeString(dir.resolve("case-2.json"), "{not json");

    assertThat(scanner().scan())
        .contains(
            "llm/xx-echo/v1/case-1.json: provenance must be one of [synthetic, public-template]"
                .replace("[synthetic, public-template]", FixtureScanner.PROVENANCES.toString()),
            "llm/xx-echo/v1/case-1.json: response contains a canary value outside placeholder form",
            "llm/xx-echo/v1/case-2.json: not valid JSON");
  }

  @Test
  void shouldReportMissingCanaryFile() throws IOException {
    Files.delete(fixtures.resolve("canaries.yml"));

    assertThat(scanner().scan()).containsExactly("fixtures/canaries.yml is missing");
  }
}
