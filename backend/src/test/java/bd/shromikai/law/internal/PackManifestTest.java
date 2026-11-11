package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FR-12: the jurisdiction pack names the official acts and the ILO conventions to ingest. */
@Tag("unit")
@Requirement("FR-12")
class PackManifestTest {

  @TempDir Path dir;

  @Test
  void shouldLoadShippedBangladeshPack() {
    PackManifest pack = PackManifest.load(Path.of("..", "data", "packs", "bd.yaml"));

    assertThat(pack.code()).isEqualTo("BD");
    assertThat(pack.portalBaseUrl()).isEqualTo("http://bdlaws.minlaw.gov.bd");
    assertThat(pack.acts())
        .extracting(ActDefinition::code)
        .containsExactly("BLA-2006", "CA-1872", "BLR-2015");
    assertThat(pack.acts().get(0).portalActId()).isEqualTo("952");
    assertThat(pack.acts().get(2).required()).isFalse();
    assertThat(pack.acts().get(2).portalActId()).isNull();
    assertThat(pack.iloConventions())
        .extracting(IloConvention::code)
        .containsExactly("C001", "C029", "C095", "C132");
  }

  @Test
  void shouldNotIngestIloConventionsNobodyReviewed_orOnesReviewedByTheirProposer() {
    PackManifest pack = PackManifest.load(Path.of("..", "data", "packs", "bd.yaml"));
    assertThat(pack.iloConventions()).noneMatch(IloConvention::reviewed);

    IloConvention self =
        new IloConvention("C001", "x", "https://example.test", "Ann", "ann", "a", "b");
    IloConvention other =
        new IloConvention("C001", "x", "https://example.test", "Ann", "Bob", "a", "b");
    IloConvention blank =
        new IloConvention("C001", "x", "https://example.test", "Ann", " ", "a", "b");

    assertThat(self.reviewed()).isFalse();
    assertThat(blank.reviewed()).isFalse();
    assertThat(other.reviewed()).isTrue();
  }

  @Test
  void shouldRejectDuplicateOrInvalidCodes() throws IOException {
    assertThatThrownBy(
            () ->
                PackManifest.load(
                    write(
                        "code: XX\nacts:\n  - {code: A-1, jurisdiction: XX, authoritative_lang: en, title_en: t}\n  - {code: A-1, jurisdiction: XX, authoritative_lang: en, title_en: t}\n")))
        .hasMessageContaining("duplicate");
    assertThatThrownBy(
            () ->
                PackManifest.load(
                    write(
                        "code: XX\nacts:\n  - {code: 1bad, jurisdiction: XX, authoritative_lang: en, title_en: t}\n")))
        .hasMessageContaining("invalid");
    assertThatThrownBy(
            () ->
                PackManifest.load(
                    write(
                        "code: XX\nacts:\n  - {code: A-1, jurisdiction: XX, authoritative_lang: fr, title_en: t}\n")))
        .hasMessageContaining("authoritative_lang");
    assertThatThrownBy(
            () ->
                PackManifest.load(
                    write(
                        "code: XX\nilo_conventions:\n  - {code: X1, title_label: t, normlex_url: u, proposed_by: p}\n")))
        .hasMessageContaining("ILO");
    assertThatThrownBy(() -> PackManifest.load(write("acts: []\n")))
        .hasMessageContaining("missing 'code'");
  }

  private Path write(String yaml) throws IOException {
    Path file = dir.resolve("pack.yaml");
    Files.writeString(file, yaml);
    return file;
  }
}
