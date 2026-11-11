package bd.shromikai.memo;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-51: average sentence length of the narrative, Bangla and English. */
@Tag("unit")
@Requirement("FR-51")
class PlainLanguageLintTest {

  @Test
  void countsWordsPerSentenceInBangla() {
    double average =
        PlainLanguageLint.averageSentenceWords("আপনার চুক্তিতে তিনটি ঝুঁকি আছে। নোটিস কম।");

    assertThat(average).isEqualTo(3.5);
  }

  @Test
  void countsWordsPerSentenceInEnglish() {
    assertThat(PlainLanguageLint.averageSentenceWords("Notice is short. Ask for more time!"))
        .isEqualTo(3.5);
  }

  @Test
  void emptyTextHasNoSentences() {
    assertThat(PlainLanguageLint.averageSentenceWords("  ")).isZero();
  }
}
