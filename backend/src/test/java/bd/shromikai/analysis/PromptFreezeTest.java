package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The P6 prompts (and their output schemas) are frozen at the checkpoint before the live model
 * calls, 2026-11-11; {@code risk-analysis} v2 was frozen the same day after a live run showed v1's
 * 1,400-token cap failing (ADR-0016). A recorded fixture stores the hash of the request rendered
 * from a prompt, and money was spent recording it, so a silent edit would invalidate it (R-05).
 * Changing a frozen file needs a new prompt version, an update of this table with the reason, and a
 * new recording. The same hashes are in {@code prompts/LOCK}, which {@code promptLint} checks.
 */
@Tag("unit")
@Requirement("FR-37")
class PromptFreezeTest {

  private static final Path RESOURCES = Path.of("src/main/resources");

  private record Frozen(String id, int version, String promptSha256, String schemaSha256) {}

  private static final List<Frozen> FROZEN =
      List.of(
          new Frozen(
              "clause-facts",
              1,
              "d97c02bbc78cb95911f7a1acf33e77e479d046e6d29746e3df4d5fce8cb3b0ce",
              "2b5228db49eb2e4521d1f326edc82973fb4e1095af6392c598ccd3a319301629"),
          new Frozen(
              "risk-analysis",
              1,
              "146938b152b6e5dedb7bd7a87288bb1f13755256e98cf5bdc75b18212a4aa18e",
              "7ef85e6e0582982a734754b71e2b27816cc8fb5f2e1eeebb5ea214e7cec4faab"),
          new Frozen(
              "risk-analysis",
              2,
              "0c341cfd5d34cd3690dcf880d6978ec717c373f709750c3fd6d1eb2a3bf117d1",
              "2f6c2f15fd1d71e9dbd993edb9801f1cc1def77b49b6f40b331417c10baeb15b"),
          new Frozen(
              "risk-citation-repair",
              1,
              "c87a8a6a7147cf1cbe70b91c2456e942c1b93f25a81a7326f988fe7ea574443e",
              "ec2f64f55b0dc63a2129ad8ff9bd770682cad24f2e715634f7cc19c406826c45"),
          new Frozen(
              "counter-offer",
              1,
              "7783820311c0190cdb44e09de3341790ab0b00f73fdd273f9bccb8ff95113331",
              "d381b7a4bcfe393c9f414c5ad6b56eb385ce33fbb94476bc47bf978f555448c6"),
          new Frozen(
              "verify-support",
              1,
              "97dc9603e26f3a161abc96472596d230e0a36f7af88b9868929e5cf2b4698d1e",
              "95ef72f5ba32d9d8f3211aabbc51ecb8824aa9bfc02c6a98a0ee84356adf3c33"),
          new Frozen(
              "verify-support-claude",
              1,
              "851105d32a9b61b7ec126b49a598abf65216fc71496775d30210521613546cab",
              "d27d1d4d2d20c91d7a6acbda4b8f926e5cb71cbccc35cda298242dee7768da88"));

  private static String hash(Path file) throws IOException {
    return Hashing.sha256Hex(Files.readString(file));
  }

  @Test
  void shouldNotChangeAFrozenPromptWithoutANewVersion() throws IOException {
    for (Frozen f : FROZEN) {
      assertThat(hash(RESOURCES.resolve("prompts/" + f.id() + "/v" + f.version() + ".md")))
          .as(f.id() + " v" + f.version() + " is frozen")
          .isEqualTo(f.promptSha256());
    }
  }

  @Test
  void shouldNotChangeAFrozenOutputSchemaWithoutANewVersion() throws IOException {
    for (Frozen f : FROZEN) {
      assertThat(
              hash(
                  RESOURCES.resolve("schemas/llm/" + f.id() + ".v" + f.version() + ".schema.json")))
          .as(f.id() + " v" + f.version() + " schema is frozen")
          .isEqualTo(f.schemaSha256());
    }
  }

  @Test
  void shouldListEveryFrozenPromptInTheLockFile() throws IOException {
    String lock = Files.readString(RESOURCES.resolve("prompts/LOCK"));
    for (Frozen f : FROZEN) {
      assertThat(lock).contains(f.id() + " v" + f.version() + " " + f.promptSha256());
    }
  }
}
