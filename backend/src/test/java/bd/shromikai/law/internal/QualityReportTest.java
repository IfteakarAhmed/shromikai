package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.law.internal.QualityReport.ActFacts;
import bd.shromikai.law.internal.QualityReport.Input;
import bd.shromikai.law.internal.QualityReport.MissingLine;
import bd.shromikai.law.internal.QualityReport.SectionFacts;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FR-17: the corpus quality report and its failure rule. */
@Tag("unit")
@Requirement("FR-17")
class QualityReportTest {

  private static final Instant AT = Instant.parse("2026-11-11T10:00:00Z");

  private static ActFacts act(String code, boolean required, String failure) {
    return new ActFacts(
        code,
        "Title " + code,
        "BD",
        "bn",
        "IN_FORCE",
        "HTML",
        "bdlaws-print-1",
        "http://example.test/" + code,
        required,
        failure,
        0,
        List.of(),
        List.of());
  }

  private static SectionFacts section(
      String act, int n, String url, boolean bn, boolean en, boolean current, boolean omitted) {
    return new SectionFacts(
        act,
        act + ":" + n + "@original",
        String.valueOf(n),
        url,
        bn,
        en,
        current,
        omitted,
        "Heading " + n);
  }

  private static List<SectionFacts> sections(String act, int count) {
    List<SectionFacts> out = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      out.add(
          section(
              act,
              i,
              "http://example.test/" + act + "/section-" + i + ".html",
              true,
              false,
              true,
              false));
    }
    return out;
  }

  private static Input input(List<ActFacts> acts, List<SectionFacts> sections) {
    return new Input(
        acts,
        sections,
        Map.of("REFERS_TO", 5L, "USES_DEFINITION", 3L),
        2,
        List.of(),
        List.of(),
        "2026.11.11-1",
        "abc1234",
        42L,
        AT);
  }

  @Test
  void shouldFailTheRun_whenASectionHasNoSourceUrl() {
    List<SectionFacts> sections = new ArrayList<>(sections("BLA", 3));
    sections.add(section("BLA", 4, " ", true, false, true, false));

    QualityReport report = QualityReport.build(input(List.of(act("BLA", true, null)), sections));

    assertThat(report.failed()).isTrue();
    assertThat(report.failures())
        .anyMatch(f -> f.contains("MISSING_SOURCE_URL") && f.contains("BLA:4@original"));
    assertThat(report.markdown()).contains("FAILED").contains("BLA:4@original");
  }

  @Test
  void shouldPass_whenEverySectionHasASourceUrl() {
    QualityReport report =
        QualityReport.build(input(List.of(act("BLA", true, null)), sections("BLA", 6)));

    assertThat(report.failed()).isFalse();
    assertThat(report.failures()).isEmpty();
    assertThat(report.markdown()).contains("PASSED");
  }

  @Test
  void shouldCountSectionsPerAct_andPercentagesWithTheirCounts() {
    List<SectionFacts> sections = new ArrayList<>(sections("BLA", 4));
    sections.add(section("BLA", 5, "http://x/5", true, false, false, true)); // omitted
    sections.add(section("BLA", 6, "http://x/6", false, false, true, false)); // empty text

    QualityReport report = QualityReport.build(input(List.of(act("BLA", true, null)), sections));
    QualityReport.ActLine line = report.acts().get(0);

    assertThat(line.versions()).isEqualTo(6);
    assertThat(line.currentSections()).isEqualTo(5);
    assertThat(line.omittedSections()).isEqualTo(1);
    assertThat(line.emptyTexts()).isEqualTo(1);
    assertThat(report.markdown()).contains("1 of 5"); // empty texts, with the count beside it
  }

  @Test
  void shouldListMissingLanguagesAndDuplicateCurrentNumbers() {
    List<SectionFacts> sections = new ArrayList<>(sections("BLA", 2));
    sections.add(
        section("BLA", 2, "http://x/dup", true, true, true, false)); // second current row of 2

    QualityReport report = QualityReport.build(input(List.of(act("BLA", true, null)), sections));

    QualityReport.ActLine line = report.acts().get(0);
    assertThat(line.duplicateNumbers()).isEqualTo(1);
    assertThat(line.missingTranslation()).isEqualTo(2); // sections lacking English text
    assertThat(report.failed()).isTrue();
    assertThat(report.failures()).anyMatch(f -> f.startsWith("DUPLICATE_CURRENT_SECTION"));
  }

  @Test
  void shouldFail_whenARequiredActCouldNotBeAcquired_butOnlyNoteAnOptionalOne() {
    QualityReport required =
        QualityReport.build(input(List.of(act("BLA", true, "HTML_UNUSABLE: no body")), List.of()));
    QualityReport optional =
        QualityReport.build(
            new Input(
                List.of(act("BLA", true, null), act("BLR", false, "NOT_PUBLISHED_ON_PORTAL")),
                sections("BLA", 6),
                Map.of(),
                0,
                List.of(),
                List.of(new MissingLine("BLR", "NOT_PUBLISHED_ON_PORTAL")),
                "2026.11.11-1",
                "abc1234",
                1L,
                AT));

    assertThat(required.failed()).isTrue();
    assertThat(required.failures()).anyMatch(f -> f.startsWith("ACT_FAILED") && f.contains("BLA"));
    assertThat(optional.failed()).isFalse();
    assertThat(optional.markdown()).contains("BLR").contains("NOT_PUBLISHED_ON_PORTAL");
  }

  @Test
  void shouldSampleFiveCurrentSections_deterministicallyForTheSeed() {
    List<SectionFacts> sections = new ArrayList<>(sections("BLA", 30));
    sections.add(
        section("BLA", 31, "http://x/31", true, false, false, true)); // omitted: never sampled
    Input a = input(List.of(act("BLA", true, null)), sections);

    QualityReport first = QualityReport.build(a);
    QualityReport second = QualityReport.build(a);
    QualityReport other =
        QualityReport.build(
            new Input(
                a.acts(),
                a.sections(),
                a.edgeCounts(),
                a.unresolvedReferences(),
                a.ilo(),
                a.missing(),
                a.corpusRelease(),
                a.gitSha(),
                7L,
                AT));

    assertThat(first.samples()).hasSize(5);
    assertThat(first.samples()).isEqualTo(second.samples());
    assertThat(first.samples()).isNotEqualTo(other.samples());
    assertThat(first.samples())
        .allSatisfy(
            s -> assertThat(s.officialUrl()).startsWith("http://example.test/BLA/section-"));
    assertThat(first.samples())
        .extracting(QualityReport.Sample::sectionKey)
        .doesNotContain("BLA:31@original");
    assertThat(first.markdown()).contains("seed 42");
  }

  @Test
  void shouldSampleAllSections_whenFewerThanFive() {
    QualityReport report =
        QualityReport.build(input(List.of(act("BLA", true, null)), sections("BLA", 3)));

    assertThat(report.samples()).hasSize(3);
  }

  @Test
  void shouldWriteMarkdownAndJsonFiles(@TempDir Path dir) throws IOException {
    QualityReport report =
        QualityReport.build(input(List.of(act("BLA", true, null)), sections("BLA", 6)));

    report.write(dir);

    JsonNode json = new ObjectMapper().readTree(dir.resolve("corpus-quality.json").toFile());
    assertThat(json.get("status").asText()).isEqualTo("PASSED");
    assertThat(json.get("corpusRelease").asText()).isEqualTo("2026.11.11-1");
    assertThat(json.get("gitSha").asText()).isEqualTo("abc1234");
    assertThat(json.get("sampleSeed").asLong()).isEqualTo(42L);
    assertThat(json.get("edges").get("REFERS_TO").asLong()).isEqualTo(5L);
    assertThat(Files.readString(dir.resolve("corpus-quality.md")))
        .contains("# Corpus quality report");
  }

  @Test
  void shouldReportEdgeCountsByType() {
    QualityReport report =
        QualityReport.build(input(List.of(act("BLA", true, null)), sections("BLA", 6)));

    assertThat(report.edges()).containsEntry("REFERS_TO", 5L).containsEntry("USES_DEFINITION", 3L);
    assertThat(report.markdown()).contains("REFERS_TO").contains("unresolved");
  }
}
