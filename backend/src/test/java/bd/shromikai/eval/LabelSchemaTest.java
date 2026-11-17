package bd.shromikai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FR-79: every label file validates against the label schema (evaluation.md §4.2), the review
 * fields are consistent with the status, and the report can say how many files are reviewed.
 */
@Tag("unit")
@Requirement("FR-79")
class LabelSchemaTest {

  private static final Path LABELS = Path.of("..", "eval", "labels");

  @TempDir Path dir;

  private static final String VALID =
      """
      contractId: syn-xx-01
      schemaVersion: 1
      status: draft
      drafted_by: Claude (AI draft)
      reviewed_by: null
      corpusRelease: 2026.11.11-1
      clauses:
        - label: "1"
          clauseType: APPOINTMENT_AND_ROLE
          paragraphIds: []
          facts: []
        - label: "4"
          clauseType: NOTICE_AND_RESIGNATION
          paragraphIds: []
          facts:
            - { parameter: NOTICE_BY_EMPLOYER, value: 7, unit: DAYS }
      risks:
        - id: G1
          clauseLabels: ["4"]
          clauseType: NOTICE_AND_RESIGNATION
          kind: CONFLICT
          severity: HIGH
          nonObvious: false
          side: BOTH
          acceptableSectionKeys: ["XX-LAB:9@original"]
          requiresCitation: true
          rationale: Toy.
      obligations: []
      """;

  private Path write(String name, String yaml) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, yaml);
    return file;
  }

  @Test
  void shouldAcceptAValidDraft() throws IOException {
    Labels.LabelFile file = Labels.load(write("syn-xx-01.yaml", VALID));
    assertThat(file.contractId()).isEqualTo("syn-xx-01");
    assertThat(file.reviewed()).isFalse();
    assertThat(file.risks()).hasSize(1);
    assertThat(file.risks().get(0).acceptableSectionKeys()).containsExactly("XX-LAB:9@original");
  }

  @Test
  void shouldRejectAReviewedFileWithoutReviewers() throws IOException {
    Path file = write("syn-xx-01.yaml", VALID.replace("status: draft", "status: reviewed"));
    assertThatThrownBy(() -> Labels.load(file)).hasMessageContaining("reviewed_by");
  }

  @Test
  void shouldRejectADraftThatNamesAReviewer() throws IOException {
    Path file =
        write(
            "syn-xx-01.yaml",
            VALID.replace(
                "reviewed_by: null",
                "reviewed_by:\n  - { name: Someone, role: law student, date: 2026-11-20 }"));
    assertThatThrownBy(() -> Labels.load(file)).hasMessageContaining("draft");
  }

  @Test
  void shouldAcceptAReviewedFileWithAReviewerNameAndDate() throws IOException {
    Path file =
        write(
            "syn-xx-01.yaml",
            VALID
                .replace("status: draft", "status: reviewed")
                .replace(
                    "reviewed_by: null",
                    "reviewed_by:\n  - { name: Someone, role: law student, date: 2026-11-20 }"));
    assertThat(Labels.load(file).reviewed()).isTrue();
  }

  @Test
  void shouldRejectUnknownKeysAndBadEnums() throws IOException {
    Path extra =
        write("syn-xx-01.yaml", VALID.replace("schemaVersion: 1", "schemaVersion: 1\nfoo: 1"));
    assertThatThrownBy(() -> Labels.load(extra)).hasMessageContaining("foo");
    Path kind =
        write(
            "syn-xx-02.yaml",
            VALID
                .replace("contractId: syn-xx-01", "contractId: syn-xx-02")
                .replace("kind: CONFLICT", "kind: GUESS"));
    assertThatThrownBy(() -> Labels.load(kind)).hasMessageContaining("kind");
  }

  @Test
  void shouldRejectAFileWhoseNameDiffersFromItsContract() throws IOException {
    Path file = write("syn-xx-09.yaml", VALID);
    assertThatThrownBy(() -> Labels.load(file)).hasMessageContaining("syn-xx-09");
  }

  @Test
  void shouldValidateEveryLabelFileInTheRepository() throws IOException {
    if (!Files.isDirectory(LABELS)) {
      return;
    }
    List<Path> files;
    try (Stream<Path> s = Files.list(LABELS)) {
      files = s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
    }
    for (Path file : files) {
      Labels.LabelFile label = Labels.load(file);
      assertThat(label.contractId()).isNotBlank();
    }
    Labels.Summary summary = Labels.summarise(files.stream().map(Labels::load).toList());
    assertThat(summary.files()).isEqualTo(files.size());
    assertThat(summary.reviewed()).isLessThanOrEqualTo(summary.files());
  }

  @Test
  void shouldExportACsvForReviewers() throws IOException {
    Labels.LabelFile file = Labels.load(write("syn-xx-01.yaml", VALID));
    String csv = Labels.csv(List.of(file));
    assertThat(csv.lines().findFirst().orElseThrow())
        .isEqualTo(
            "contractId,status,riskId,clauseLabels,clauseType,kind,severity,nonObvious,side,"
                + "acceptableSectionKeys,requiresCitation,reviewerVerdict,reviewerNote");
    assertThat(csv)
        .contains(
            "syn-xx-01,draft,G1,4,NOTICE_AND_RESIGNATION,CONFLICT,HIGH,false,BOTH,XX-LAB:9@original,true,,");
  }
}
