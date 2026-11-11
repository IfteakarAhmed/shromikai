package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Quote-match algorithm shared by verify, rules and law (architecture §7.3 check b). */
@Tag("unit")
@Requirement("FR-16")
class QuoteMatcherTest {

  private static final String TEXT =
      "The Fictional Employment Act says that every worker shall receive a written notice "
          + "of thirty days before the end of service, except in cases of gross misconduct.";

  @Test
  void shouldScoreOne_whenQuoteIsSubstringAfterNormalisation() {
    assertThat(QuoteMatcher.similarity("a written NOTICE of thirty days", TEXT)).isEqualTo(1.0);
  }

  @Test
  void shouldIgnoreDigitScriptAndInvisibleCharacters() {
    assertThat(QuoteMatcher.similarity("ধারা ২৬ এর শ্রমিক", "এই ধারা 26 এর শ্র‌মিক বলিতে"))
        .isEqualTo(1.0);
  }

  @Test
  void shouldScoreBelowOne_whenQuoteHasSmallEdits() {
    double score = QuoteMatcher.similarity("a written notice of thirty dayz before", TEXT);

    assertThat(score).isBetween(0.9, 1.0).isNotEqualTo(1.0);
  }

  @Test
  void shouldScoreLow_whenQuoteIsUnrelated() {
    assertThat(QuoteMatcher.similarity("completely different sentence about machines", TEXT))
        .isLessThan(0.5);
  }

  @Test
  void shouldMatchAnyPositionInText() {
    assertThat(QuoteMatcher.similarity("gross misconduct", TEXT)).isEqualTo(1.0);
    assertThat(QuoteMatcher.similarity("fictional employment", TEXT)).isEqualTo(1.0);
  }

  @Test
  void shouldScoreZero_whenQuoteIsEmpty() {
    assertThat(QuoteMatcher.similarity("", TEXT)).isZero();
    assertThat(QuoteMatcher.similarity("   ", TEXT)).isZero();
  }

  @Test
  void shouldComputeEditDistance_withSellersAlgorithm() {
    assertThat(QuoteMatcher.minEditDistance("kitten", "a sitting cat")).isEqualTo(2);
    assertThat(QuoteMatcher.minEditDistance("abc", "xxabcxx")).isZero();
    assertThat(QuoteMatcher.minEditDistance("abc", "")).isEqualTo(3);
  }

  @Test
  void shouldReportNormalisedLength() {
    assertThat(QuoteMatcher.normalisedLength("  A;B  ")).isEqualTo(3);
    assertThat(QuoteMatcher.similarity("abc", "abd")).isCloseTo(2.0 / 3.0, within(1e-9));
  }
}
