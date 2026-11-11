package bd.shromikai.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-26: legacy-font Bangla converts to Unicode; plain text is left alone. */
@Tag("unit")
@Requirement("FR-26")
class BijoyConverterTest {

  // legacy (Bijoy/SutonnyMJ) text -> Unicode. Words are common, non-legal vocabulary.
  private static final Map<String, String> WORDS =
      Map.ofEntries(
          Map.entry("Avwg", "আমি"),
          Map.entry("evsjv", "বাংলা"),
          Map.entry("gvbyl", "মানুষ"),
          Map.entry("evsjv‡`k", "বাংলাদেশ"),
          Map.entry("Kg©Pvix", "কর্মচারী"),
          Map.entry("‡eZb", "বেতন"),
          Map.entry("gvwmK", "মাসিক"),
          Map.entry("wnmve", "হিসাব"),
          Map.entry("UvKv", "টাকা"),
          Map.entry("wk¶v", "শিক্ষা"),
          Map.entry("‡`vlx", "দোষী"),
          Map.entry("mgq", "সময়"),
          Map.entry("Kvh©", "কার্য"));

  @Test
  void shouldConvertCommonWordsExactly() {
    WORDS.forEach(
        (legacy, unicode) -> assertThat(BijoyConverter.toUnicode(legacy)).isEqualTo(unicode));
  }

  @Test
  void shouldReachCharacterAccuracyOfAtLeast097OnASentence() {
    String legacy = "Avwg gvwmK UvKv ‡eZb cvB| Kg©Pvix evsjv‡`‡k Kv‡Ri mgq";
    String expected = "আমি মাসিক টাকা বেতন পাই। কর্মচারী বাংলাদেশে কাজের সময়";

    String actual = BijoyConverter.toUnicode(legacy);

    assertThat(accuracy(expected, actual)).isGreaterThanOrEqualTo(0.97);
  }

  @Test
  void shouldRecogniseLegacyTextAndLeaveEnglishAlone() {
    assertThat(BijoyConverter.looksLegacy("Avwg gvwmK ‡eZb UvKv cvB Kg©Pvix evsjv‡`k")).isTrue();
    String english = "This agreement is made between the Employer and the Worker.";
    assertThat(BijoyConverter.looksLegacy(english)).isFalse();
    assertThat(BijoyConverter.looksLegacy("কর্মচারী মাসিক বেতন পাবেন")).isFalse();
    assertThat(BijoyConverter.toUnicode(english))
        .isNotEqualTo(english); // letters map, so callers gate on looksLegacy
  }

  @Test
  void shouldMeasureTheShareOfBengaliLetters() {
    assertThat(BijoyConverter.bengaliShare("বেতন")).isEqualTo(1.0);
    assertThat(BijoyConverter.bengaliShare("abc")).isEqualTo(0.0);
    assertThat(BijoyConverter.bengaliShare("")).isEqualTo(0.0);
  }

  /** Share of positions where both strings agree (strings here have equal length when right). */
  private static double accuracy(String expected, String actual) {
    int n = Math.max(expected.length(), actual.length());
    int same = 0;
    for (int i = 0; i < Math.min(expected.length(), actual.length()); i++) {
      if (expected.charAt(i) == actual.charAt(i)) {
        same++;
      }
    }
    return n == 0 ? 1 : (double) same / n;
  }
}
