package bd.shromikai.law;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-13, FR-16: Latin key form of printed section numbers and the section key format. */
@Tag("unit")
@Requirement("FR-13")
class SectionNumbersTest {

  static Stream<Arguments> numbers() {
    return Stream.of(
        Arguments.of("১০০", "100"),
        Arguments.of("৩ক", "3A"),
        Arguments.of("১৯৬খ", "196B"),
        Arguments.of("৩৪৮গ", "348C"),
        Arguments.of("27A", "27A"),
        Arguments.of("76-123", "76-123"),
        Arguments.of("২৬-২৮ক", "26-28A"),
        Arguments.of(" ৫ ", "5"));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("numbers")
  void shouldConvertPrintedNumbersToLatinKeyForm(String printed, String latin) {
    assertThat(SectionNumbers.toLatin(printed)).contains(latin);
  }

  @ParameterizedTest
  @MethodSource("notNumbers")
  void shouldRejectTextThatIsNotASectionNumber(String text) {
    assertThat(SectionNumbers.toLatin(text)).isEmpty();
  }

  static Stream<String> notNumbers() {
    return Stream.of("", "ক", "abc", "১২৩।", "১ ২", "(১)");
  }

  @Test
  void shouldFindLeadingNumberWithAnyOfTheThreeFullStops() {
    assertThat(SectionNumbers.leadingNumber("১৭৫। শ্রমিকের সংজ্ঞা")).contains("১৭৫");
    assertThat(SectionNumbers.leadingNumber("৩৫৩৷ রহিতকরণ")).contains("৩৫৩");
    assertThat(SectionNumbers.leadingNumber("19A. Power to set aside")).contains("19A");
    assertThat(SectionNumbers.leadingNumber("239-266. [Repealed]")).contains("239-266");
    assertThat(SectionNumbers.leadingNumber("Extent Commencement")).isEmpty();
    assertThat(SectionNumbers.withoutLeadingNumber("১৭৫। শ্রমিকের সংজ্ঞা"))
        .isEqualTo("শ্রমিকের সংজ্ঞা");
    assertThat(SectionNumbers.withoutLeadingNumber("Extent")).isEqualTo("Extent");
  }

  @Test
  void shouldRoundTripSectionKeys() {
    SectionKey key = new SectionKey("BLA-2006", "27(3A)", "amend-2026");

    assertThat(key.toString()).isEqualTo("BLA-2006:27(3A)@amend-2026");
    assertThat(SectionKey.parse(key.toString())).contains(key);
    assertThat(SectionKey.parse("not a key")).isEqualTo(Optional.empty());
    assertThat(SectionKey.parse("BLA:১০০@original")).isEmpty(); // Latin digits only
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> new SectionKey("x", "1", "Original")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
