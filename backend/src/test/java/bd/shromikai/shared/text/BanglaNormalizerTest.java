package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-14: each step of architecture §7.2 on a concrete example. */
@Tag("unit")
@Requirement("FR-14")
class BanglaNormalizerTest {

  @Test
  void shouldComposeUnicode_whenInputIsDecomposed() {
    assertThat(BanglaNormalizer.matchingForm("é")).isEqualTo("é");
  }

  @Test
  void shouldUseKhandaTa_whenTaHasantaZwjAppears() {
    assertThat(BanglaNormalizer.matchingForm("সত্‍"))
        .isEqualTo(BanglaNormalizer.matchingForm("সৎ"));
  }

  @Test
  void shouldRemoveInvisibleCharacters() {
    assertThat(BanglaNormalizer.matchingForm("শ্র‌মি‍ক​﻿­"))
        .isEqualTo(BanglaNormalizer.matchingForm("শ্রমিক"));
  }

  @Test
  void shouldMapBanglaDigitsDandaQuotesAndDashes() {
    assertThat(BanglaNormalizer.matchingForm("ধারা ২৬(১)।")).isEqualTo("ধারা 26(1).");
    assertThat(BanglaNormalizer.matchingForm("“A” ‘b’ — c")).isEqualTo("a b c");
  }

  @Test
  void shouldLowercaseLatinAndCollapseWhitespace() {
    assertThat(BanglaNormalizer.matchingForm("  Section \t 27(3A)  \n of  the ACT "))
        .isEqualTo("section 27(3a) of the act");
  }

  @Test
  void shouldReplaceOtherPunctuationWithSpace() {
    assertThat(BanglaNormalizer.matchingForm("a;b:c,d[e]f")).isEqualTo("a b c d e f");
  }

  @Test
  void shouldKeepCombiningMarksOfBanglaLetters() {
    assertThat(BanglaNormalizer.matchingForm("কি")).isEqualTo("কি");
  }

  @Test
  void shouldHandleEmptyInput() {
    assertThat(BanglaNormalizer.matchingForm("")).isEmpty();
    assertThat(BanglaNormalizer.matchingForm("  \n")).isEmpty();
  }

  @Test
  void shouldConvertDigitsBothWays() {
    assertThat(BanglaNormalizer.toLatinDigits("১৯৬ক-২০২৬")).isEqualTo("196ক-2026");
    assertThat(BanglaNormalizer.toBanglaDigits("Act 2006")).isEqualTo("Act ২০০৬");
  }
}
