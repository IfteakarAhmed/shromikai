package bd.shromikai.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-25: repeated headers and footers, hyphenation, paragraphs and language. */
@Tag("unit")
@Requirement("FR-25")
class HeaderFooterAndHyphenationTest {

  @Test
  void shouldKeepARepeatedHeaderOnceAndDropPageNumbers() {
    List<List<String>> pages =
        List.of(
            List.of("EXAMPLE LTD - EMPLOYMENT AGREEMENT", "1. First clause text.", "Page 1 of 3"),
            List.of("EXAMPLE LTD - EMPLOYMENT AGREEMENT", "2. Second clause text.", "Page 2 of 3"),
            List.of("EXAMPLE LTD - EMPLOYMENT AGREEMENT", "3. Third clause text.", "Page 3 of 3"));

    List<List<String>> out = HeaderFooterRemover.remove(pages);

    long headers =
        out.stream().flatMap(List::stream).filter(l -> l.startsWith("EXAMPLE LTD")).count();
    assertThat(headers).isEqualTo(1);
    assertThat(out.stream().flatMap(List::stream)).noneMatch(l -> l.startsWith("Page "));
    assertThat(out.get(1)).containsExactly("2. Second clause text.");
  }

  @Test
  void shouldNotRemoveALineThatRepeatsOnlyTwiceOrInTheMiddleOfPages() {
    List<List<String>> pages =
        List.of(
            List.of("Same line", "a", "b", "c", "d"),
            List.of("Same line", "a", "b", "c", "d"),
            List.of("x", "b", "b", "b", "d"));

    List<List<String>> out = HeaderFooterRemover.remove(pages);

    assertThat(out.get(1)).contains("Same line");
  }

  @Test
  void shouldRepairHyphenatedWordsOnlyBeforeLowerCase() {
    List<String> out =
        HyphenationRepair.repair(
            List.of(
                "The termi-", "nation clause applies to non-", "Compete rules, see 12-", "34."));

    assertThat(out)
        .containsExactly("The termination clause applies to non-", "Compete rules, see 12-", "34.");
  }

  @Test
  void shouldSplitOnBlankLinesAndNumberingMarkersAndJoinWrappedLines() {
    var lines =
        List.of(
            new Paragrapher.Line(1, "EMPLOYMENT AGREEMENT"),
            new Paragrapher.Line(1, ""),
            new Paragrapher.Line(1, "1. The Worker joins as an operator and"),
            new Paragrapher.Line(1, "reports to the supervisor."),
            new Paragrapher.Line(1, "(a) first item"),
            new Paragrapher.Line(2, "২. মাসিক বেতন ২৫,০০০ টাকা।"),
            new Paragrapher.Line(2, "(ক) প্রথম উপধারা"));

    List<Paragrapher.Para> paras = Paragrapher.split(lines);

    assertThat(paras)
        .extracting(Paragrapher.Para::text)
        .containsExactly(
            "EMPLOYMENT AGREEMENT",
            "1. The Worker joins as an operator and reports to the supervisor.",
            "(a) first item",
            "২. মাসিক বেতন ২৫,০০০ টাকা।",
            "(ক) প্রথম উপধারা");
    assertThat(paras.get(3).page()).isEqualTo(2);
  }

  @Test
  void shouldDetectLanguage() {
    assertThat(Paragrapher.language("মাসিক বেতন পঁচিশ হাজার টাকা")).isEqualTo("bn");
    assertThat(Paragrapher.language("The monthly wage is paid")).isEqualTo("en");
    assertThat(Paragrapher.language("মাসিক বেতন BDT 25,000 monthly wage paid on time"))
        .isEqualTo("mixed");
  }
}
