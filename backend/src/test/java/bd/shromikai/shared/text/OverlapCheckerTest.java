package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Glossary "contract text": 8 or more consecutive tokens (FR-50, R-02). */
@Tag("unit")
@Requirement("FR-50")
class OverlapCheckerTest {

  private static final String SOURCE =
      "The employer shall give the worker ninety days written notice before ending service"
          + " except for gross misconduct.";

  private final OverlapChecker checker = OverlapChecker.of(SOURCE);

  @Test
  void shouldDetectEightCopiedTokens() {
    assertThat(
            checker.overlaps("Summary: employer shall give the worker ninety days written notice."))
        .isTrue();
  }

  @Test
  void shouldNotFlagSevenTokensOrAParaphrase() {
    assertThat(checker.overlaps("employer shall give the worker ninety days")).isFalse();
    assertThat(checker.overlaps("The notice period given to the worker is unusually long."))
        .isFalse();
  }

  @Test
  void shouldIgnoreCasePunctuationAndDigitScript() {
    OverlapChecker bangla =
        OverlapChecker.of("বেতন ১২ তারিখের মধ্যে প্রতি মাসে পরিশোধ করা হবে বলে ধার্য");

    assertThat(bangla.overlaps("বেতন 12 তারিখের মধ্যে, প্রতি মাসে; পরিশোধ করা হবে।")).isTrue();
  }

  @Test
  void shouldFindNothing_whenThereAreNoSourcesOrNoText() {
    assertThat(new OverlapChecker(java.util.List.of()).overlaps(SOURCE)).isFalse();
    assertThat(checker.overlaps("")).isFalse();
  }
}
