package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.memo.RedlineDiff.Op;
import bd.shromikai.memo.RedlineDiff.Segment;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-52: word-level diff with insertions and deletions marked. */
@Tag("unit")
@Requirement("FR-52")
class RedlineDiffTest {

  private static String join(List<Segment> diff, Op... ops) {
    List<Op> wanted = List.of(ops);
    return diff.stream()
        .filter(s -> wanted.contains(s.op()))
        .map(Segment::text)
        .collect(Collectors.joining())
        .replaceAll("\\s+", " ")
        .trim();
  }

  @Test
  void marksAnInsertedWord() {
    List<Segment> diff =
        RedlineDiff.words("Notice is thirty days", "Notice is at least thirty days");

    assertThat(diff).anyMatch(s -> s.op() == Op.INSERT && s.text().trim().equals("at least"));
    assertThat(diff).noneMatch(s -> s.op() == Op.DELETE);
  }

  @Test
  void marksADeletedAndAReplacedWord() {
    List<Segment> diff = RedlineDiff.words("Probation is six months", "Probation is three months");

    assertThat(diff).anyMatch(s -> s.op() == Op.DELETE && s.text().trim().equals("six"));
    assertThat(diff).anyMatch(s -> s.op() == Op.INSERT && s.text().trim().equals("three"));
  }

  @Test
  void bothTextsCanBeRebuiltFromTheSegments() {
    String original = "The employer may terminate without notice, at any time.";
    String proposed = "The employer may terminate with thirty days notice.";

    List<Segment> diff = RedlineDiff.words(original, proposed);

    assertThat(join(diff, Op.EQUAL, Op.DELETE)).isEqualTo(original);
    assertThat(join(diff, Op.EQUAL, Op.INSERT)).isEqualTo(proposed);
  }

  @Test
  void worksOnBanglaText() {
    String original = "শিক্ষানবিশকাল হবে ছয় মাস।";
    String proposed = "শিক্ষানবিশকাল হবে তিন মাস।";

    List<Segment> diff = RedlineDiff.words(original, proposed);

    assertThat(diff).anyMatch(s -> s.op() == Op.DELETE && s.text().trim().equals("ছয়"));
    assertThat(diff).anyMatch(s -> s.op() == Op.INSERT && s.text().trim().equals("তিন"));
    assertThat(join(diff, Op.EQUAL, Op.INSERT)).isEqualTo(proposed);
  }

  @Test
  void treatsPunctuationAsItsOwnToken() {
    List<Segment> diff = RedlineDiff.words("Pay monthly.", "Pay monthly, in cash.");

    assertThat(diff).noneMatch(s -> s.op() == Op.DELETE && s.text().contains("monthly"));
    assertThat(join(diff, Op.EQUAL, Op.INSERT)).isEqualTo("Pay monthly, in cash.");
  }

  @Test
  void identicalTextsAreOneEqualSegment() {
    assertThat(RedlineDiff.words("same text", "same text"))
        .containsExactly(new Segment(Op.EQUAL, "same text"));
  }

  @Test
  void anInsertedClauseIsOneInsertSegment() {
    assertThat(RedlineDiff.words("", "A new clause."))
        .containsExactly(new Segment(Op.INSERT, "A new clause."));
  }

  @Test
  void adjacentSegmentsOfTheSameOperationAreMerged() {
    List<Segment> diff = RedlineDiff.words("a b c d", "a x y d");

    for (int i = 1; i < diff.size(); i++) {
      assertThat(diff.get(i).op()).isNotEqualTo(diff.get(i - 1).op());
    }
  }
}
