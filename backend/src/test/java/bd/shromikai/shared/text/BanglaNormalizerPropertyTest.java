package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/** FR-14: properties of the one normaliser used by verify, rules and law (architecture §7.2). */
@Tag("unit")
@Requirement("FR-14")
class BanglaNormalizerPropertyTest {

  private static final String BANGLA_DIGITS = "০১২৩৪৫৬৭৮৯";
  private static final String MIXED_ALPHABET =
      BANGLA_DIGITS
          + "0123456789"
          + "শ্রমিকআইনধারা"
          + "্‍‌​﻿­"
          + "।॥.,;:()[]{}'\"‘’“”-–—_ \t\n "
          + "abcXYZÉéİ"
          + "́";

  @Provide
  Arbitrary<String> banglaDigitStrings() {
    return Arbitraries.strings().withChars(BANGLA_DIGITS.toCharArray()).ofMaxLength(40);
  }

  @Provide
  Arbitrary<String> latinDigitStrings() {
    return Arbitraries.strings().withChars("0123456789".toCharArray()).ofMaxLength(40);
  }

  @Provide
  Arbitrary<String> mixedText() {
    return Arbitraries.strings().withChars(MIXED_ALPHABET.toCharArray()).ofMaxLength(60);
  }

  @Property
  void shouldRoundTripBanglaDigits(@ForAll("banglaDigitStrings") String bangla) {
    assertThat(BanglaNormalizer.toBanglaDigits(BanglaNormalizer.toLatinDigits(bangla)))
        .isEqualTo(bangla);
  }

  @Property
  void shouldRoundTripLatinDigits(@ForAll("latinDigitStrings") String latin) {
    assertThat(BanglaNormalizer.toLatinDigits(BanglaNormalizer.toBanglaDigits(latin)))
        .isEqualTo(latin);
  }

  @Property
  void shouldBeIdempotent_whenNormalisingForMatching(@ForAll("mixedText") String text) {
    String once = BanglaNormalizer.matchingForm(text);

    assertThat(BanglaNormalizer.matchingForm(once)).isEqualTo(once);
  }

  @Property
  void shouldBeIdempotent_forArbitraryUnicode(@ForAll String text) {
    String once = BanglaNormalizer.matchingForm(text);

    assertThat(BanglaNormalizer.matchingForm(once)).isEqualTo(once);
  }

  @Property
  void shouldProduceOnlyMatchingCharacters(@ForAll("mixedText") String text) {
    String form = BanglaNormalizer.matchingForm(text);

    assertThat(form).doesNotContain("  ").doesNotStartWith(" ").doesNotEndWith(" ");
    assertThat(form).doesNotContainPattern("[০-৯​‌‍﻿­]");
  }

  @Property
  void shouldNeverModifyDisplayText(@ForAll("mixedText") String display) {
    NormalisedText text = NormalisedText.of(display);

    assertThat(text.display()).isSameAs(display);
    assertThat(text.matching()).isEqualTo(BanglaNormalizer.matchingForm(display));
  }

  @Property
  void shouldMatchAcrossDigitScripts(@ForAll("banglaDigitStrings") String bangla) {
    String latin = BanglaNormalizer.toLatinDigits(bangla);

    assertThat(BanglaNormalizer.matchingForm("ধারা " + bangla))
        .isEqualTo(BanglaNormalizer.matchingForm("ধারা " + latin));
  }
}
