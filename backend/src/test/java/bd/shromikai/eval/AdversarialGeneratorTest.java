package bd.shromikai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.eval.AdversarialGenerator.ClaimDraft;
import bd.shromikai.eval.AdversarialGenerator.SectionRow;
import bd.shromikai.testsupport.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-79, M-04, M-05: the adversarial citation set (evaluation.md §4.4) is built from the corpus,
 * deterministically for a seed; fabricated classes really are fabricated (a key not in the corpus,
 * a quote with 15 % of its characters replaced), and nothing is reviewed by default. Fictional act
 * XX-LAB only (R-10).
 */
@Tag("unit")
@Requirement("FR-79")
class AdversarialGeneratorTest {

  private static List<SectionRow> corpus() {
    List<SectionRow> rows = new ArrayList<>();
    for (int i = 1; i <= 30; i++) {
      rows.add(
          new SectionRow(
              "XX-LAB:" + i + "@original",
              "XX-LAB",
              String.valueOf(i),
              true,
              "The employer shall keep register number "
                  + i
                  + " for every worker and shall show it to the inspector on request at any time.",
              "en"));
    }
    rows.add(
        new SectionRow(
            "XX-LAB:7@old",
            "XX-LAB",
            "7",
            false,
            "An earlier text of section seven about the register of workers kept by employers.",
            "en"));
    return rows;
  }

  private static List<ClaimDraft> drafts() {
    List<ClaimDraft> drafts = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      drafts.add(
          new ClaimDraft(
              "XX-LAB:" + i + "@original",
              "register number " + i + " for every worker",
              "The employer must keep register " + i + " for each worker.",
              i <= 6 ? "The employer need not keep any register for workers." : null,
              i % 2 == 0 ? "registers" : "inspection"));
    }
    return drafts;
  }

  @Test
  void shouldBeDeterministicForASeed() {
    var a = AdversarialGenerator.generate(corpus(), drafts(), 42);
    var b = AdversarialGenerator.generate(corpus(), drafts(), 42);
    assertThat(a).isEqualTo(b);
    assertThat(AdversarialGenerator.generate(corpus(), drafts(), 7)).isNotEqualTo(a);
  }

  @Test
  void shouldBuildEveryClassWithDraftReviewStatus() {
    var cases = AdversarialGenerator.generate(corpus(), drafts(), 42);
    Set<String> classes = cases.stream().map(AdversarialCase::cls).collect(Collectors.toSet());
    assertThat(classes)
        .contains(
            "NONEXISTENT_SECTION",
            "NOT_CURRENT",
            "MISQUOTE",
            "UNRELATED_CLAIM",
            "CONTRADICTED_CLAIM",
            "VALID_CONTROL");
    assertThat(cases).allMatch(c -> c.reviewedBy() == null);
    assertThat(cases.stream().map(AdversarialCase::id).distinct().count()).isEqualTo(cases.size());
    assertThat(cases)
        .filteredOn(c -> c.cls().equals("VALID_CONTROL"))
        .allMatch(c -> c.expectedStatus().equals("VERIFIED"));
    assertThat(cases)
        .filteredOn(c -> !c.cls().equals("VALID_CONTROL"))
        .allMatch(c -> c.expectedStatus().equals("NOT_VERIFIED"));
  }

  @Test
  void shouldUseOnlyKeysAbsentFromTheCorpusForNonexistentSections() {
    Set<String> keys = corpus().stream().map(SectionRow::sectionKey).collect(Collectors.toSet());
    AdversarialGenerator.generate(corpus(), drafts(), 42).stream()
        .filter(c -> c.cls().equals("NONEXISTENT_SECTION"))
        .forEach(c -> assertThat(keys).doesNotContain(c.sectionKey()));
  }

  @Test
  void shouldReplaceAboutFifteenPercentOfAMisquote() {
    var corpus = corpus();
    for (AdversarialCase c : AdversarialGenerator.generate(corpus, drafts(), 42)) {
      if (!c.cls().equals("MISQUOTE")) {
        continue;
      }
      String original =
          corpus.stream()
              .filter(r -> r.sectionKey().equals(c.sectionKey()))
              .findFirst()
              .orElseThrow()
              .text();
      assertThat(original).doesNotContain(c.quote());
      int changed = 0;
      String source = AdversarialGenerator.sourceQuote(original);
      for (int i = 0; i < c.quote().length(); i++) {
        if (c.quote().charAt(i) != source.charAt(i)) {
          changed++;
        }
      }
      assertThat(changed).isGreaterThanOrEqualTo((int) Math.floor(0.15 * countLetters(source)));
    }
  }

  @Test
  void shouldPairAnUnrelatedClaimWithAnotherSectionsQuote() {
    AdversarialGenerator.generate(corpus(), drafts(), 42).stream()
        .filter(c -> c.cls().equals("UNRELATED_CLAIM"))
        .forEach(
            c -> {
              assertThat(c.construction()).contains("claim written for");
              assertThat(c.construction()).doesNotContain("claim written for " + c.sectionKey());
            });
  }

  private static int countLetters(String s) {
    return (int) s.codePoints().filter(Character::isLetter).count();
  }
}
