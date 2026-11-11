package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The six P6 prompts (and their output schemas) are frozen at the checkpoint before the first live
 * model call, 2026-11-11. A recorded fixture stores the hash of the request rendered from a prompt,
 * and money was spent recording it, so a silent edit would invalidate it (R-05). Changing a frozen
 * file needs a new prompt version {@code v2}, an update of this table with the reason, and a new
 * recording.
 */
@Tag("unit")
@Requirement("FR-37")
class PromptFreezeTest {

  private static final Path RESOURCES = Path.of("src/main/resources");

  private static final Map<String, String> PROMPTS = new LinkedHashMap<>();
  private static final Map<String, String> SCHEMAS = new LinkedHashMap<>();

  static {
    PROMPTS.put("clause-facts", "d97c02bbc78cb95911f7a1acf33e77e479d046e6d29746e3df4d5fce8cb3b0ce");
    PROMPTS.put(
        "risk-analysis", "146938b152b6e5dedb7bd7a87288bb1f13755256e98cf5bdc75b18212a4aa18e");
    PROMPTS.put(
        "risk-citation-repair", "c87a8a6a7147cf1cbe70b91c2456e942c1b93f25a81a7326f988fe7ea574443e");
    PROMPTS.put(
        "counter-offer", "7783820311c0190cdb44e09de3341790ab0b00f73fdd273f9bccb8ff95113331");
    PROMPTS.put(
        "verify-support", "97dc9603e26f3a161abc96472596d230e0a36f7af88b9868929e5cf2b4698d1e");
    PROMPTS.put(
        "verify-support-claude",
        "851105d32a9b61b7ec126b49a598abf65216fc71496775d30210521613546cab");
    SCHEMAS.put("clause-facts", "2b5228db49eb2e4521d1f326edc82973fb4e1095af6392c598ccd3a319301629");
    SCHEMAS.put(
        "risk-analysis", "7ef85e6e0582982a734754b71e2b27816cc8fb5f2e1eeebb5ea214e7cec4faab");
    SCHEMAS.put(
        "risk-citation-repair", "ec2f64f55b0dc63a2129ad8ff9bd770682cad24f2e715634f7cc19c406826c45");
    SCHEMAS.put(
        "counter-offer", "d381b7a4bcfe393c9f414c5ad6b56eb385ce33fbb94476bc47bf978f555448c6");
    SCHEMAS.put(
        "verify-support", "95ef72f5ba32d9d8f3211aabbc51ecb8824aa9bfc02c6a98a0ee84356adf3c33");
    SCHEMAS.put(
        "verify-support-claude",
        "d27d1d4d2d20c91d7a6acbda4b8f926e5cb71cbccc35cda298242dee7768da88");
  }

  private static String hash(Path file) throws IOException {
    return Hashing.sha256Hex(Files.readString(file));
  }

  @Test
  void shouldNotChangeAFrozenPromptWithoutANewVersion() throws IOException {
    for (var entry : PROMPTS.entrySet()) {
      assertThat(hash(RESOURCES.resolve("prompts/" + entry.getKey() + "/v1.md")))
          .as(entry.getKey() + " v1 is frozen")
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  void shouldNotChangeAFrozenOutputSchemaWithoutANewVersion() throws IOException {
    for (var entry : SCHEMAS.entrySet()) {
      assertThat(hash(RESOURCES.resolve("schemas/llm/" + entry.getKey() + ".v1.schema.json")))
          .as(entry.getKey() + " v1 schema is frozen")
          .isEqualTo(entry.getValue());
    }
  }
}
