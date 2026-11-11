package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.text.BanglaNormalizer;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-33, R-02: the five synthetic contracts are marked, complete and listed as canaries. */
@Tag("unit")
@Requirement("FR-33")
@Requirement("NFR-05")
class SyntheticContractsTest {

  private final PiiDetector detector = new PiiDetector();

  static List<String> contracts() {
    return SyntheticContracts.IDS;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldBeMarkedSyntheticWithTheMarkerInThreeClauses(String id) {
    String text = SyntheticContracts.text(id);

    assertThat(text.lines().findFirst().orElseThrow()).containsAnyOf("সিনথেটিক", "SYNTHETIC");
    assertThat(text.split(SyntheticContracts.marker(id), -1)).hasSize(4);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldListEveryIdentifierTheDetectorsFindInItsEntitiesFile(String id) {
    String text = SyntheticContracts.text(id);
    List<String> listed = SyntheticContracts.entities(id).stream().map(e -> e.value()).toList();

    for (PiiMatch match : detector.detect(text)) {
      assertThat(listed).contains(text.substring(match.start(), match.end()));
    }
    for (SyntheticContracts.Entity entity : SyntheticContracts.entities(id)) {
      assertThat(BanglaNormalizer.toLatinDigits(text))
          .contains(BanglaNormalizer.toLatinDigits(entity.value()));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldListItsNidPhoneAndEmailValuesInCanariesYml(String id) throws Exception {
    String canaries = Files.readString(Path.of("src/test/resources/fixtures/canaries.yml"));

    for (String value : SyntheticContracts.values(id, "PERSON", "NID", "PHONE", "EMAIL")) {
      assertThat(canaries).contains(value);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldHaveProvenanceSynthetic(String id) throws Exception {
    String meta =
        Files.readString(Path.of("..", "eval", "contracts", "synthetic", id + ".meta.yml"));

    assertThat(meta).contains("provenance: synthetic", "source: team", "licence:");
  }
}
