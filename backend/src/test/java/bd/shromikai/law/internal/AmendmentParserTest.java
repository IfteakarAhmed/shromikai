package bd.shromikai.law.internal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.law.internal.VersionPlanner.Action;
import bd.shromikai.law.internal.VersionPlanner.Existing;
import bd.shromikai.law.internal.VersionPlanner.Incoming;
import bd.shromikai.law.internal.VersionPlanner.Plan;
import bd.shromikai.testsupport.Requirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-13: amendment notes become version rows; only the latest version is current. */
@Tag("unit")
@Requirement("FR-13")
class AmendmentParserTest {

  private static final String H1 = "a".repeat(64);
  private static final String H2 = "b".repeat(64);
  private static final String H3 = "c".repeat(64);

  @Test
  void shouldClassifyKindsInBanglaAndEnglish() {
    assertThat(AmendmentParser.kind("ধারা ১৭৫ ... ধারাবলে প্রতিস্থাপিত।"))
        .isEqualTo(AmendmentKind.SUBSTITUTED);
    assertThat(AmendmentParser.kind("ধারা ৩ক ... ধারাবলে সন্নিবেশিত।"))
        .isEqualTo(AmendmentKind.INSERTED);
    assertThat(AmendmentParser.kind("দফা (ঘ) ... ধারাবলে বিলুপ্ত।"))
        .isEqualTo(AmendmentKind.OMITTED);
    assertThat(AmendmentParser.kind("Section 19A was inserted by section 3"))
        .isEqualTo(AmendmentKind.INSERTED);
    assertThat(AmendmentParser.kind("was substituted for the words"))
        .isEqualTo(AmendmentKind.SUBSTITUTED);
    assertThat(AmendmentParser.kind("Repealed by the Sale of Goods Act"))
        .isEqualTo(AmendmentKind.OMITTED);
    assertThat(AmendmentParser.kind("see the schedule")).isEqualTo(AmendmentKind.OTHER);
  }

  @Test
  void shouldReadAmendingActCitation_inBanglaAndEnglish() {
    AmendmentNote bn =
        AmendmentParser.note(
            "ধারা ১৭৫ বাংলাদেশ শ্রম (সংশোধন) আইন, ২০২৬ (২০২৬ সনের ৪৩ নং আইন) এর ৩২ ধারাবলে প্রতিস্থাপিত।");
    AmendmentNote en =
        AmendmentParser.note(
            "Section 19A was inserted by section 3 of the Indian Contract (Amendment) Act, 1899 (Act No. VI of 1899)");

    assertThat(bn.amendingActId()).isEqualTo("2026-43");
    assertThat(bn.kind()).isEqualTo(AmendmentKind.SUBSTITUTED);
    assertThat(en.amendingActId()).isEqualTo("1899-vi");
    assertThat(AmendmentParser.note("কোনো আইন উল্লেখ নেই বিলুপ্ত।").amendingActId()).isNull();
  }

  @Test
  void shouldLabelVersionsByLatestAmendingAct() {
    Map<String, String> acts = new LinkedHashMap<>();
    acts.put("2013-30", "(২০১৩ সনের ৩০ নং আইন)");
    acts.put("2026-43", "(২০২৬ সনের ৪৩ নং আইন)");
    Map<String, String> labels = AmendmentParser.labels(acts);

    assertThat(labels)
        .containsEntry("2013-30", "amend-2013")
        .containsEntry("2026-43", "amend-2026");
    List<AmendmentNote> notes =
        List.of(
            AmendmentParser.note("... (২০১৩ সনের ৩০ নং আইন) এর ৩ ধারাবলে সন্নিবেশিত।"),
            AmendmentParser.note("... (২০২৬ সনের ৪৩ নং আইন) এর ৩২ ধারাবলে প্রতিস্থাপিত।"));
    assertThat(AmendmentParser.latestLabel(notes, labels)).isEqualTo("amend-2026");
    assertThat(AmendmentParser.latestLabel(List.of(), labels)).isEqualTo("original");
    assertThat(AmendmentParser.latestLabel(List.of(AmendmentParser.note("বিলুপ্ত।")), labels))
        .isEqualTo("original");
  }

  @Test
  void shouldAddNumber_whenTwoDetectedActsShareAYear() {
    Map<String, String> acts = new LinkedHashMap<>();
    acts.put("2025-7", "(২০২৫ সনের ৭ নং অধ্যাদেশ)");
    acts.put("2025-9", "(২০২৫ সনের ৯ নং আইন)");

    assertThat(AmendmentParser.labels(acts).values())
        .containsExactly("amend-2025-7", "amend-2025-9");
  }

  @Test
  void shouldRecogniseOmissionMarkers() {
    assertThat(AmendmentParser.isOmissionMarker("[***]")).isTrue();
    assertThat(AmendmentParser.isOmissionMarker("[বিলুপ্ত]")).isTrue();
    assertThat(AmendmentParser.isOmissionMarker("১৮৪। [বিলুপ্ত]")).isTrue();
    assertThat(AmendmentParser.isOmissionMarker("১০০। কোন প্রাপ্তবয়স্ক শ্রমিক")).isFalse();
    assertThat(AmendmentParser.isOmissionMarker("")).isFalse();
  }

  @Test
  void substitutedSection_createsNewCurrentVersion_andKeepsOldOneNonCurrent() {
    List<Existing> stored = List.of(new Existing(10, "original", H1, true));

    Plan plan = VersionPlanner.plan(stored, new Incoming("amend-2026", H2, true));

    assertThat(plan.action()).isEqualTo(Action.INSERT);
    assertThat(plan.label()).isEqualTo("amend-2026");
    assertThat(plan.demote()).containsExactly(10L); // the old row stays, no longer current
    assertThat(plan.amendedFrom()).isEqualTo(10L); // AMENDED_BY edge old -> new
  }

  @Test
  void shouldDoNothing_whenSameTextUnderSameLabelIsAlreadyCurrent() {
    List<Existing> stored = List.of(new Existing(10, "amend-2026", H2, true));

    Plan plan = VersionPlanner.plan(stored, new Incoming("amend-2026", H2, true));

    assertThat(plan.action()).isEqualTo(Action.NOOP);
    assertThat(plan.demote()).isEmpty();
  }

  @Test
  void shouldRevisionTheLabel_whenTextChangesWithoutNewAmendingAct() {
    List<Existing> stored = List.of(new Existing(10, "original", H1, true));

    Plan plan = VersionPlanner.plan(stored, new Incoming("original", H2, true));

    assertThat(plan.label()).isEqualTo("original-r2");
    assertThat(plan.demote()).containsExactly(10L);

    List<Existing> twice =
        List.of(new Existing(10, "original", H1, false), new Existing(11, "original-r2", H2, true));
    assertThat(VersionPlanner.plan(twice, new Incoming("original", H3, true)).label())
        .isEqualTo("original-r3");
  }

  @Test
  void shouldReactivate_whenSourceReturnsToStoredText() {
    List<Existing> stored =
        List.of(new Existing(10, "original", H1, false), new Existing(11, "amend-2026", H2, true));

    Plan plan = VersionPlanner.plan(stored, new Incoming("original", H1, true));

    assertThat(plan.action()).isEqualTo(Action.REACTIVATE);
    assertThat(plan.reactivateId()).isEqualTo(10L);
    assertThat(plan.demote()).containsExactly(11L);
  }

  @Test
  void shouldInsertFirstVersion_whenNothingIsStored() {
    Plan plan = VersionPlanner.plan(List.of(), new Incoming("original", H1, true));

    assertThat(plan.action()).isEqualTo(Action.INSERT);
    assertThat(plan.demote()).isEmpty();
    assertThat(plan.amendedFrom()).isNull();
  }

  @Test
  void omittedSection_demotesTheCurrentVersion() {
    List<Existing> stored = List.of(new Existing(10, "original", H1, true));

    Plan plan = VersionPlanner.plan(stored, new Incoming("amend-2026", H2, false));

    assertThat(plan.action()).isEqualTo(Action.INSERT);
    assertThat(plan.demote()).containsExactly(10L);
  }

  @Test
  void shouldStripRevisionSuffixFromBase() {
    assertThat(VersionPlanner.baseOf("amend-2026-r3")).isEqualTo("amend-2026");
    assertThat(VersionPlanner.baseOf("amend-2025-7")).isEqualTo("amend-2025-7");
    assertThat(VersionPlanner.baseOf("original")).isEqualTo("original");
  }
}
