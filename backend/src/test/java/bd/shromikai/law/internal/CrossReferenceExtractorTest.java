package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.law.internal.CrossReferenceExtractor.Reference;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-16: REFERS_TO patterns, in Bangla and English, and the false positives to avoid. */
@Tag("unit")
@Requirement("FR-16")
class CrossReferenceExtractorTest {

  private final CrossReferenceExtractor extractor =
      new CrossReferenceExtractor(Set.of("বাংলাদেশ শ্রম আইন", "Contract Act"));

  static Stream<Arguments> references() {
    return Stream.of(
        Arguments.of("ধারা ২৬(১) এর বিধান", List.of("26(1)")),
        Arguments.of("section 27(3A) applies", List.of("27(3A)")),
        Arguments.of("sub-section (2) of section 100 shall", List.of("100(2)")),
        Arguments.of("sections 24 to 26 of this Act", List.of("24", "25", "26")),
        Arguments.of("ধারা ২৪ হইতে ২৬ এর বিধান", List.of("24", "25", "26")),
        Arguments.of("ধারা ১২, ১৬, ১৭ এবং ১৮ প্রয়োগের ক্ষেত্রে", List.of("12", "16", "17", "18")),
        Arguments.of("sections 24, 25 and 26", List.of("24", "25", "26")),
        Arguments.of("ধারা ৩ক এর অধীন", List.of("3A")),
        Arguments.of("ধারা ১ এর উপ-ধারা (৪) ও ২ এর দফা (৬৫) তে", List.of("1(4)", "2")),
        Arguments.of("এই আইনের ২৮ ধারা অনুযায়ী কোন শ্রমিকের", List.of("28")),
        Arguments.of("ধারা ১০৮ এর বিধান সাপেক্ষে", List.of("108")),
        Arguments.of("under section 19A of the Act", List.of("19A")),
        Arguments.of("ধারা ২৬ এবং ধারা ২৭ এর", List.of("26", "27")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("references")
  void shouldExtractReferences(String text, List<String> expected) {
    assertThat(extractor.extract(text))
        .extracting(Reference::label)
        .containsExactlyElementsOf(expected);
  }

  static Stream<String> falsePositives() {
    return Stream.of(
        "১১ অক্টোবর ২০০৬ তারিখে প্রকাশিত",
        "25th April, 1872",
        "11 October 2006",
        "(২০২৬ সনের ৪৩ নং আইন) এর ৩২ ধারাবলে প্রতিস্থাপিত",
        "(Act No. IX of 1872)",
        "উপ-ধারা (২) এর দফা (ক)",
        "sub-section (2) applies",
        "ধারা সম্পর্কে কিছু বলা নেই",
        "the section was inserted",
        "দফা (৬৫) এ সংজ্ঞায়িত",
        "section 2006 of the year");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("falsePositives")
  void shouldNotExtractFalsePositives(String text) {
    assertThat(extractor.extract(text)).isEmpty();
  }

  static Stream<String> otherActs() {
    return Stream.of(
        "ধারা ১৭৫ বাংলাদেশ শ্রম (সংশোধন) আইন, ২০২৬ এ প্রতিস্থাপিত",
        "under section 5 of the Sale of Goods Act, 1930",
        "section 12 of the Penal Code");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("otherActs")
  void shouldSkipReferencesToOtherActs(String text) {
    assertThat(extractor.extract(text)).isEmpty();
  }

  @Test
  void shouldKeepReferencesToThisAct_whenItIsNamed() {
    assertThat(extractor.extract("section 5 of the Contract Act"))
        .extracting(Reference::label)
        .containsExactly("5");
    assertThat(extractor.extract("section 5 of this Act"))
        .extracting(Reference::label)
        .containsExactly("5");
  }

  @Test
  void shouldReportSectionAndSubsectionSeparately() {
    Reference ref = extractor.extract("ধারা ২৬(১) এর").get(0);

    assertThat(ref.sectionNo()).isEqualTo("26");
    assertThat(ref.subsection()).isEqualTo("1");
  }

  @Test
  void shouldDeduplicateRepeatedReferences() {
    assertThat(extractor.extract("ধারা ২৬ ... আবার ধারা ২৬ এর")).hasSize(1);
  }

  @Test
  void shouldNotExpandAbsurdRanges() {
    assertThat(extractor.extract("sections 1 to 900"))
        .extracting(Reference::label)
        .containsExactly("1", "900");
  }
}
