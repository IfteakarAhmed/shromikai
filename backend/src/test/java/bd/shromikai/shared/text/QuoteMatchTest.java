package bd.shromikai.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-43: quote matching on normalised text. The sections below are fictional (pack xx, R-10): they
 * say nothing about any real law.
 */
@Tag("unit")
@Requirement("FR-43")
class QuoteMatchTest {

  private static final double THRESHOLD = 0.92;

  private static final String SECTION_BN =
      "ধারা ২৬। কোনো শ্রমিকের চাকরির অবসান করিবার পূর্বে কাল্পনিক ৩০ দিনের লিখিত নোটিশ প্রদান করিতে হইবে,"
          + " তবে গুরুতর অসদাচরণের ক্ষেত্রে ইহা প্রযোজ্য হইবে না।";

  private static final String SECTION_EN =
      "Section 12. The fictional employer shall give the worker 30 days' written notice, in the"
          + " manner stated in this fictional Act, before the end of service.";

  @Test
  void shouldMatchBanglaDigitsAgainstLatinDigits() {
    assertThat(
            QuoteMatcher.similarity(
                "কাল্পনিক ৩০ দিনের লিখিত নোটিশ প্রদান করিতে হইবে",
                SECTION_BN.replace('৩', '3').replace('০', '0')))
        .isEqualTo(1.0);
  }

  @Test
  void shouldIgnoreZeroWidthJoinersAndNonJoiners() {
    String withJoiners = "কাল্পনিক ৩০ দিনের‌ লিখিত নোটিশ‍ প্রদান করিতে";

    assertThat(QuoteMatcher.similarity(withJoiners, SECTION_BN)).isEqualTo(1.0);
  }

  @Test
  void shouldMatchTaWithHasantaAndZwjAsKhandaTa() {
    assertThat(BanglaNormalizer.matchingForm("উৎস"))
        .isEqualTo(BanglaNormalizer.matchingForm("উত\u09CD\u200Dস"));
    assertThat(QuoteMatcher.similarity("উত\u09CD\u200Dস আইন", "এই উৎস আইন বলিতে")).isEqualTo(1.0);
  }

  @Test
  void shouldIgnoreExtraSpacesAndPunctuationDifferences() {
    String quote =
        "  the fictional   employer shall give the worker 30 days'  written notice;  in the manner ";

    assertThat(QuoteMatcher.similarity(quote, SECTION_EN)).isEqualTo(1.0);
  }

  @Test
  void shouldMatchDandaAsFullStop() {
    assertThat(QuoteMatcher.similarity("প্রযোজ্য হইবে না।", "প্রযোজ্য হইবে না.")).isEqualTo(1.0);
  }

  @Test
  void shouldPassATruncatedQuote_whenItIsStillASubstring() {
    assertThat(QuoteMatcher.similarity("shall give the worker 30 days' written", SECTION_EN))
        .isEqualTo(1.0);
  }

  @Test
  void shouldPassASmallTypo_butFailANearMissFabrication() {
    double typo =
        QuoteMatcher.similarity(
            "the fictional employer shall give the worker 30 days written notis", SECTION_EN);
    double fabricated =
        QuoteMatcher.similarity(
            "the fictional employer shall give the worker 90 days' written notice and a bonus",
            SECTION_EN);
    double invented =
        QuoteMatcher.similarity(
            "every worker is entitled to a company car after one year of continuous service",
            SECTION_EN);

    assertThat(typo).isGreaterThanOrEqualTo(THRESHOLD);
    assertThat(fabricated).isLessThan(THRESHOLD);
    assertThat(invented).isLessThan(0.5);
  }

  @Test
  void shouldNotMatchTheOtherLanguage() {
    assertThat(
            QuoteMatcher.similarity(
                "written notice of thirty days before the end of service", SECTION_BN))
        .isLessThan(0.5);
  }
}
