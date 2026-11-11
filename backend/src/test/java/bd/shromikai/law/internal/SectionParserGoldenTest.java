package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-13: saved pages of the official portal (fixtures/law/, provenance in PROVENANCE.md) parse into
 * the expected sections; three sections are compared with a snapshot.
 */
@Tag("unit")
@Requirement("FR-13")
class SectionParserGoldenTest {

  private final ParsedAct labour = LawFixtures.parse("labour-act-2006");
  private final ParsedAct contract = LawFixtures.parse("contract-act-1872");

  @Test
  void shouldParseExpectedSections_whenLabourActFixtureIsRead() {
    assertThat(labour.problems()).isEmpty();
    assertThat(labour.sections())
        .extracting(ParsedSection::sectionNo)
        .containsExactly("1", "2", "3", "3A", "26", "27", "100", "175", "184");
    assertThat(nfc(labour.title())).isEqualTo(nfc("বাংলাদেশ শ্রম আইন, ২০০৬"));
    assertThat(labour.actNumberText()).isEqualTo("( ২০০৬ সনের ৪২ নং আইন )");
    assertThat(labour.publishedOn()).isEqualTo(LocalDate.of(2006, 10, 11));
  }

  @Test
  void shouldParseExpectedSections_whenContractActFixtureIsRead() {
    assertThat(contract.problems()).isEmpty();
    assertThat(contract.sections())
        .extracting(ParsedSection::sectionNo)
        .containsExactly("1", "2", "19A", "30A", "76-123", "239-266");
    assertThat(contract.title()).isEqualTo("The Contract Act, 1872");
    assertThat(contract.publishedOn()).isEqualTo(LocalDate.of(1872, 4, 25));
  }

  @Test
  void snapshotOfSection100_plainSectionWithOneProviso() {
    ParsedSection s = section(labour, "100");

    assertThat(s.sectionNoOriginal()).isEqualTo("১০০");
    assertThat(nfc(s.heading())).isEqualTo(nfc("দৈনিক কর্মঘণ্টা"));
    assertThat(nfc(s.chapterNo())).isEqualTo(nfc("নবম অধ্যায়"));
    assertThat(nfc(s.chapterName())).isEqualTo(nfc("কমর্ঘন্টা ও ছুটি"));
    assertThat(s.sourceUrl()).startsWith("http://bdlaws.minlaw.gov.bd/act-952/section-");
    assertThat(s.omitted()).isFalse();
    assertThat(s.notes()).isEmpty();
    assertThat(nfc(s.text()))
        .isEqualTo(
            nfc(
                "১০০। কোন প্রাপ্তবয়স্ক শ্রমিক কোন প্রতিষ্ঠানে সাধারণতঃ দৈনিক আট ঘণ্টার অধিক সময় কাজ"
                    + " করিবেন না বা তাহাকে দিয়ে কাজ করানো যাইবে নাঃ\n"
                    + "তবে শর্ত থাকে যে, ধারা ১০৮ এর বিধান সাপেক্ষে কোন প্রতিষ্ঠানে উক্তরূপ কোন শ্রমিক"
                    + " দৈনিক দশ ঘণ্টা পর্যন্ত ও কাজ করিতে পারিবেন।"));
  }

  @Test
  void snapshotOfSection3A_letterSuffixAndInsertionNote() {
    ParsedSection s = section(labour, "3A");

    assertThat(s.sectionNoOriginal()).isEqualTo("৩ক");
    assertThat(nfc(s.heading())).isEqualTo(nfc("ঠিকাদার সংস্থা রেজিস্ট্রেশন"));
    assertThat(nfc(s.chapterName())).isEqualTo(nfc("নিয়োগ ও চাকুরীর শর্তাবলী"));
    assertThat(nfc(s.text()))
        .startsWith(nfc("[৩ক। (১) অন্য কোন আইনে ভিন্নতর যাহাই কিছু থাকুক না কেন"));
    assertThat(s.notes()).hasSize(1);
    AmendmentNote note = s.notes().get(0);
    assertThat(note.kind()).isEqualTo(AmendmentKind.INSERTED);
    assertThat(note.amendingActId()).isEqualTo("2013-30");
    assertThat(nfc(note.text())).startsWith(nfc("ধারা ৩ক বাংলাদেশ শ্রম (সংশোধন) আইন, ২০১৩"));
  }

  @Test
  void snapshotOfSection184_omittedSectionKeepsSourceMarker() {
    ParsedSection s = section(labour, "184");

    assertThat(s.omitted()).isTrue();
    assertThat(s.text()).isEqualTo("[***]");
    assertThat(nfc(s.heading())).isEqualTo(nfc("[বিলুপ্ত]"));
    assertThat(s.notes())
        .singleElement()
        .extracting(AmendmentNote::kind)
        .isEqualTo(AmendmentKind.OMITTED);
  }

  @Test
  void shouldMarkRepealedRangesOmitted_inContractAct() {
    ParsedSection s = section(contract, "76-123");

    assertThat(s.omitted()).isTrue();
    assertThat(s.sectionNoOriginal()).isEqualTo("76-123");
    assertThat(s.text()).startsWith("[Repealed by the Sale of Goods Act, 1930");
  }

  @Test
  void shouldAppendUnnumberedRowsToPreviousSection_neverInventNumbers() {
    ParsedSection s = section(contract, "1");

    assertThat(s.mergedRows()).isEqualTo(3);
    assertThat(s.text())
        .startsWith(
            "1. This Act may be called the Contract Act, 1872.\nExtent Commencement\nIt extends")
        .contains("Enactments repealed\nNothing herein contained");
    assertThat(contract.sections()).extracting(ParsedSection::sectionNo).doesNotHaveDuplicates();
  }

  @Test
  void shouldKeepDisplayTextUntouched() {
    ParsedSection s = section(labour, "26");

    assertThat(s.text()).startsWith("২৬\u09F7 (১)"); // Bangla digits and U+09F7 are not normalised
    assertThat(s.text()).doesNotContain(" ").doesNotContain("  ");
  }

  @Test
  void shouldNotNormaliseDisplayText_sourceKeepsPrecomposedYa() {
    // The source writes the letter য় as U+09DF; NFC would turn it into U+09AF U+09BC. Display
    // text keeps the source form; only the matching form is normalised (FR-14).
    ParsedSection s = section(labour, "175");

    assertThat(s.chapterNo()).contains("\u09DF").doesNotContain("\u09AF\u09BC");
  }

  @Test
  void shouldSplitSubsections() {
    ParsedSection s = section(labour, "175");

    assertThat(s.subsections())
        .extracting(ParsedSection.Subsection::label)
        .containsExactly("(১)", "(২)");
    assertThat(section(labour, "2").subsections()).hasSizeGreaterThan(50);
  }

  @Test
  void shouldTrackChaptersAndSourceOrder() {
    assertThat(labour.sections())
        .extracting(ParsedSection::ordinal)
        .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(nfc(section(labour, "175").chapterNo())).isEqualTo(nfc("ত্রয়োদশ অধ্যায়"));
    assertThat(nfc(section(labour, "175").chapterName()))
        .isEqualTo(nfc("ট্রেড ইউনিয়ন এবং শিল্প সম্পর্ক"));
  }

  @Test
  void shouldFailTheAct_whenIndexAndPrintViewDisagree() {
    String index = LawFixtures.read("labour-act-2006", "index.html");
    String extra =
        index.replace(
            "</section></body>",
            "<p class=\"act-section-name \"><a href=\"/act-952/section-1.html\"> ৯৯৯। কাল্পনিক </a></p></section></body>");

    ParsedAct act =
        new ActParser(LawFixtures.BASE_URL)
            .parse(extra, LawFixtures.read("labour-act-2006", "print.html"));

    assertThat(act.sections()).isEmpty();
    assertThat(act.problems()).singleElement().asString().startsWith("COUNT_MISMATCH");
  }

  @Test
  void shouldDetectAmendingActsFromFootnotes() {
    assertThat(labour.amendingActs()).containsKeys("2026-43", "2013-30", "2018-58");
    assertThat(labour.amendingActs().get("2026-43")).isEqualTo("(২০২৬ সনের ৪৩ নং আইন)");
    assertThat(contract.amendingActs()).containsKey("1899-vi");
  }

  private static String nfc(String text) {
    return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
  }

  private static ParsedSection section(ParsedAct act, String sectionNo) {
    List<ParsedSection> found =
        act.sections().stream().filter(s -> s.sectionNo().equals(sectionNo)).toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }
}
