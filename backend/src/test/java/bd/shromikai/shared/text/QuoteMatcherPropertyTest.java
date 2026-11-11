package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/** FR-43 properties of {@link QuoteMatcher} (test-strategy.md §8). */
@Tag("unit")
@Requirement("FR-43")
class QuoteMatcherPropertyTest {

  private static final double THRESHOLD = 0.92;

  @Provide
  Arbitrary<String> sectionTexts() {
    return Arbitraries.strings()
        .withCharRange('a', 'z')
        .withChars(' ')
        .ofMinLength(120)
        .ofMaxLength(300);
  }

  @Property(tries = 100)
  void shouldScoreOneForAnySubstringOfAtLeastTwentyCharacters(
      @ForAll("sectionTexts") String text, @ForAll int start, @ForAll int length) {
    String normalised = BanglaNormalizer.matchingForm(text);
    if (normalised.length() < 40) {
      return;
    }
    int from = Math.floorMod(start, normalised.length() - 20);
    int to = Math.min(normalised.length(), from + 20 + Math.floorMod(length, 40));
    String quote = normalised.substring(from, to);
    if (quote.strip().length() < 20) {
      return;
    }

    assertThat(QuoteMatcher.similarity(quote, text)).isEqualTo(1.0);
  }

  @Property(tries = 100)
  void shouldFailTheThresholdWhenTenPercentIsReplacedByCharactersAbsentFromTheSection(
      @ForAll("sectionTexts") String text, @ForAll int start) {
    String normalised = BanglaNormalizer.matchingForm(text);
    if (normalised.length() < 80) {
      return;
    }
    int from = Math.floorMod(start, normalised.length() - 60);
    char[] quote = normalised.substring(from, from + 60).toCharArray();
    // Replace 8 of the 60 characters (13 %), spread out, by a digit that the section lacks.
    for (int i = 3; i < 60; i += 7) {
      quote[i] = '7';
    }
    String section = text.replace('7', '1');

    assertThat(QuoteMatcher.similarity(new String(quote), section)).isLessThan(THRESHOLD);
  }
}
