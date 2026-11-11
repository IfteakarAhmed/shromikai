package bd.shromikai.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.law.IloMappingLoader.Row;
import bd.shromikai.law.IloMappingLoader.Status;
import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FR-16: unreviewed rows and rows reviewed by their author are flagged and never usable; a reviewed
 * row whose ILO quote is missing from the corpus stops startup. Uses only the fictional pack {@code
 * xx} (R-10).
 */
@Tag("unit")
@Requirement("FR-16")
class IloMappingLoaderTest {

  private static final String ILO_TEXT =
      "The fictional convention says that hours of work shall not exceed the limit set by "
          + "the fictional schedule, and that rest shall be given every week.";

  private static final IloMappingLoader.CorpusLookup CORPUS =
      key ->
          Map.of(
                      "XX-ILO:2@original",
                      ILO_TEXT,
                      "XX-ACT:5@original",
                      "A fictional national rule about hours of work.")
                  .containsKey(key)
              ? Optional.of(
                  key.startsWith("XX-ILO")
                      ? ILO_TEXT
                      : "A fictional national rule about hours of work.")
              : Optional.empty();

  @TempDir Path dir;

  private Path write(String yaml) throws IOException {
    Path file = dir.resolve("bd-ilo.yaml");
    Files.writeString(file, yaml);
    return file;
  }

  private static String row(String quote, boolean reviewed, String author, String reviewer) {
    return """
        rows:
          - bd_section_key: XX-ACT:5@original
            ilo_section_key: XX-ILO:2@original
            ilo_quote: "%s"
            claim: "The national rule aligns with the fictional convention on hours."
            authored_by: "%s"
            reviewed: %s
            reviewed_by: %s
        """
        .formatted(quote, author, reviewed, reviewer == null ? "null" : "\"" + reviewer + "\"");
  }

  private static final String GOOD_QUOTE =
      "hours of work shall not exceed the limit set by the fictional schedule";

  @Test
  void shouldFlagUnreviewedRows_andNeverTreatThemAsUsable() throws IOException {
    List<Row> rows =
        IloMappingLoader.load(write(row(GOOD_QUOTE, false, "Author A", null)), CORPUS, true);

    assertThat(rows)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.status()).isEqualTo(Status.UNREVIEWED);
              assertThat(r.usable()).isFalse();
            });
  }

  @Test
  void shouldFlagRowsReviewedByTheirAuthor() throws IOException {
    List<Row> same =
        IloMappingLoader.load(write(row(GOOD_QUOTE, true, "Author A", "author a")), CORPUS, true);
    List<Row> none =
        IloMappingLoader.load(write(row(GOOD_QUOTE, true, "Author A", null)), CORPUS, true);

    assertThat(same)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.status()).isEqualTo(Status.SELF_REVIEWED);
              assertThat(r.usable()).isFalse();
            });
    assertThat(none.get(0).status()).isEqualTo(Status.SELF_REVIEWED);
  }

  @Test
  void shouldAcceptRowReviewedByAnotherPerson_whenQuoteIsInTheCorpus() throws IOException {
    List<Row> rows =
        IloMappingLoader.load(write(row(GOOD_QUOTE, true, "Author A", "Reviewer B")), CORPUS, true);

    assertThat(rows)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.status()).isEqualTo(Status.REVIEWED);
              assertThat(r.usable()).isTrue();
              assertThat(r.reviewedBy()).isEqualTo("Reviewer B");
            });
  }

  @Test
  void shouldToleratePunctuationAndCaseDifferencesInTheQuote() throws IOException {
    String quote = "Hours of Work shall not exceed the limit set by the fictional schedule;";

    assertThat(
            IloMappingLoader.load(write(row(quote, true, "A", "B")), CORPUS, true).get(0).usable())
        .isTrue();
  }

  @Test
  void shouldStopStartup_whenReviewedQuoteIsMissingFromTheCorpus() throws IOException {
    Path file =
        write(
            row(
                "this sentence does not occur anywhere in the fictional convention text",
                true,
                "A",
                "B"));

    assertThatThrownBy(() -> IloMappingLoader.load(file, CORPUS, true))
        .isInstanceOf(IloMappingException.class)
        .hasMessageContaining("row 1")
        .hasMessageContaining("not found in XX-ILO:2@original");
  }

  @Test
  void shouldStopStartup_whenReviewedRowPointsAtAnUnknownSection() throws IOException {
    Path file =
        write(row(GOOD_QUOTE, true, "A", "B").replace("XX-ILO:2@original", "XX-ILO:9@original"));

    assertThatThrownBy(() -> IloMappingLoader.load(file, CORPUS, true))
        .isInstanceOf(IloMappingException.class)
        .hasMessageContaining("is not a current section");
  }

  @Test
  void shouldNotCheckQuotes_beforeTheFirstCorpusRelease() throws IOException {
    Path file =
        write(
            row(
                "this sentence does not occur anywhere in the fictional convention text",
                true,
                "A",
                "B"));

    assertThat(IloMappingLoader.load(file, CORPUS, false))
        .singleElement()
        .extracting(Row::usable)
        .isEqualTo(true);
  }

  @Test
  void shouldRejectTooShortReviewedQuote() throws IOException {
    assertThatThrownBy(
            () -> IloMappingLoader.load(write(row("hours", true, "A", "B")), CORPUS, true))
        .isInstanceOf(IloMappingException.class)
        .hasMessageContaining("20 to 300");
  }

  @Test
  void shouldRejectMalformedRows() throws IOException {
    assertThatThrownBy(
            () ->
                IloMappingLoader.load(
                    write("rows:\n  - bd_section_key: XX-ACT:5@original\n"), CORPUS, true))
        .isInstanceOf(IloMappingException.class)
        .hasMessageContaining("missing 'ilo_section_key'");
    assertThatThrownBy(
            () ->
                IloMappingLoader.load(
                    write(
                        row(GOOD_QUOTE, false, "A", null)
                            .replace("XX-ACT:5@original", "not a key")),
                    CORPUS,
                    true))
        .isInstanceOf(IloMappingException.class)
        .hasMessageContaining("bad bd_section_key");
    assertThatThrownBy(
            () ->
                IloMappingLoader.load(
                    write(
                        row(GOOD_QUOTE, false, "A", null)
                            .replace("reviewed: false", "reviewed: \"yes\"")),
                    CORPUS,
                    true))
        .isInstanceOf(IloMappingException.class)
        .hasMessageContaining("'reviewed' must be true or false");
  }

  @Test
  void shouldReturnNoRows_whenFileIsMissingOrEmptyList() throws IOException {
    assertThat(IloMappingLoader.load(dir.resolve("absent.yaml"), CORPUS, true)).isEmpty();
    assertThat(IloMappingLoader.load(write("rows: []\n"), CORPUS, true)).isEmpty();
    assertThat(IloMappingLoader.load(write("# only comments\n"), CORPUS, true)).isEmpty();
  }

  @Test
  void shippedMappingFileStartsWithNoRowMarkedReviewed() {
    Path shipped = Path.of("..", "data", "mappings", "bd-ilo.yaml");

    List<Row> rows = IloMappingLoader.load(shipped, key -> Optional.empty(), false);

    assertThat(rows).noneMatch(Row::reviewedFlag);
  }
}
