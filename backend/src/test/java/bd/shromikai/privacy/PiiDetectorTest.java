package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** FR-28: deterministic detectors, table-driven, Bangla and English, Bangla digits. */
@Tag("unit")
@Requirement("FR-28")
class PiiDetectorTest {

  private final PiiDetector detector = new PiiDetector();

  static Stream<Arguments> positives() {
    return Stream.of(
        Arguments.of("Mobile: +8801712345678 for contact", PiiType.PHONE, "+8801712345678"),
        Arguments.of("Call 01712-345678 today", PiiType.PHONE, "01712-345678"),
        Arguments.of("ফোন: ০১৭১২৩৪৫৬৭৮ এই নম্বরে", PiiType.PHONE, "০১৭১২৩৪৫৬৭৮"),
        Arguments.of("Phone 8801912345678.", PiiType.PHONE, "8801912345678"),
        Arguments.of("Salary via bKash 01712345678 monthly", PiiType.MOBILE_WALLET, "01712345678"),
        Arguments.of("বেতন নগদ ০১৮১২৩৪৫৬৭৮ নম্বরে", PiiType.MOBILE_WALLET, "০১৮১২৩৪৫৬৭৮"),
        Arguments.of("Email rahim.test@example.com now", PiiType.EMAIL, "rahim.test@example.com"),
        Arguments.of("NID No: 1234567890", PiiType.NID, "1234567890"),
        Arguments.of("National ID 1234567890123 issued", PiiType.NID, "1234567890123"),
        Arguments.of("জাতীয় পরিচয়পত্র নং ১২৩৪৫৬৭৮৯০১২৩", PiiType.NID, "১২৩৪৫৬৭৮৯০১২৩"),
        Arguments.of("এনআইডি: ১২৩৪৫৬৭৮৯০", PiiType.NID, "১২৩৪৫৬৭৮৯০"),
        Arguments.of("NID 1990 1234 56789 0123", PiiType.NID, "1990 1234 56789 0123"),
        Arguments.of("Number 19901234567890123 is on file", PiiType.NID, "19901234567890123"),
        Arguments.of(
            "Birth Registration No. 19901234567890123", PiiType.BIRTH_REG, "19901234567890123"),
        Arguments.of("TIN: 123456789012", PiiType.TIN, "123456789012"),
        Arguments.of("টিআইএন ১২৩৪৫৬৭৮৯০১২", PiiType.TIN, "১২৩৪৫৬৭৮৯০১২"),
        Arguments.of("Account No. 1234567890123", PiiType.BANK_ACCOUNT, "1234567890123"),
        Arguments.of("ব্যাংক অ্যাকাউন্ট ১২৩৪৫৬৭৮৯০১২৩", PiiType.BANK_ACCOUNT, "১২৩৪৫৬৭৮৯০১২৩"),
        Arguments.of(
            "Card 4111 1111 1111 1111 on file", PiiType.CARD_NUMBER, "4111 1111 1111 1111"),
        Arguments.of("Passport No: AB1234567", PiiType.PASSPORT, "AB1234567"),
        Arguments.of("Date of birth: 12/05/1990", PiiType.DOB, "12/05/1990"),
        Arguments.of("জন্ম তারিখ: ১২/০৫/১৯৯০", PiiType.DOB, "১২/০৫/১৯৯০"),
        Arguments.of("Born on 5 March 1991 in Dhaka", PiiType.DOB, "5 March 1991"),
        Arguments.of("DOB 1990-05-12", PiiType.DOB, "1990-05-12"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("positives")
  void shouldDetectTheIdentifierWithItsExactSpan(String text, PiiType type, String value) {
    List<PiiMatch> matches = detector.detect(text);

    assertThat(matches).hasSize(1);
    PiiMatch match = matches.get(0);
    assertThat(match.type()).isEqualTo(type);
    assertThat(text.substring(match.start(), match.end())).isEqualTo(value);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "The monthly wage is BDT 25,000 for 6 months of probation.",
        "বেতন ২৫,০০০ টাকা এবং ৬ মাস শিক্ষানবিশকাল।",
        "Employment starts on 01/01/2026 and ends on 31/12/2026.",
        "Section 1234567890 of the Act applies.",
        "Clause 12345 and clause 67890.",
        "Total 5000000000 taka over the contract.",
        "Joining date 2026-01-01.",
        "Overtime is paid at 2 times the basic wage.",
        "Reference ZQXJ-CANARY-1A2B3C4D appears in this clause."
      })
  void shouldKeepMoneyDurationsAndEmploymentDates(String text) {
    assertThat(detector.detect(text)).isEmpty();
  }

  @Test
  void shouldNotTreatAWordContainingTinAsATaxNumber() {
    assertThat(detector.detect("Meeting number 123456789012 was held")).isEmpty();
  }

  @Test
  void shouldReturnSortedNonOverlappingSpansForMixedText() {
    String text = "Mail a@b.co or call 01712345678; NID 1234567890.";

    List<PiiMatch> matches = detector.detect(text);

    assertThat(matches)
        .extracting(PiiMatch::type)
        .containsExactly(PiiType.EMAIL, PiiType.PHONE, PiiType.NID);
    for (int i = 1; i < matches.size(); i++) {
      assertThat(matches.get(i).start()).isGreaterThanOrEqualTo(matches.get(i - 1).end());
    }
  }

  @Test
  void shouldReportWhetherTextContainsPii() {
    assertThat(detector.containsPii("call 01712345678")).isTrue();
    assertThat(detector.containsPii("no identifiers here")).isFalse();
  }

  @org.junit.jupiter.api.Test
  void shouldLabelAPhoneShapedNumberNextToAnNidKeywordAsNid() {
    // 13 digits starting 8801x look like a phone number; next to "NID No:" they are an NID.
    var matches = detector.detect("NID No: 8801712345678 (verified)");

    org.assertj.core.api.Assertions.assertThat(matches).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(matches.get(0).type()).isEqualTo(PiiType.NID);
  }

  @org.junit.jupiter.api.Test
  void shouldStillLabelAPhoneNumberWithoutAnNidKeywordAsPhone() {
    var matches = detector.detect("Call 8801712345678 today");

    org.assertj.core.api.Assertions.assertThat(matches).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(matches.get(0).type()).isEqualTo(PiiType.PHONE);
  }
}
