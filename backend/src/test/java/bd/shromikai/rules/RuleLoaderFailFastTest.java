package bd.shromikai.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bd.shromikai.law.LawReadService;
import bd.shromikai.law.Section;
import bd.shromikai.testsupport.Requirement;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** FR-36: an invalid active rule stops the application from starting (architecture §9.1). */
@Tag("unit")
@Requirement("FR-36")
class RuleLoaderFailFastTest {

  @TempDir Path dir;

  private static String rule(String quote, String status, String reviewer) {
    return """
        - id: xx-notice-min
          clause_type: NOTICE_AND_RESIGNATION
          fact: NOTICE_BY_EMPLOYER
          comparator: MIN
          value: 14
          unit: DAYS
          applies_to_category: null
          legal_basis:
            act: XX-LAB
            section: "9"
            quote: "%s"
          severity: HIGH
          explanation_bn: "কাল্পনিক ব্যাখ্যা পরীক্ষার জন্য"
          explanation_en: "Fictional explanation for tests"
          authored_by: "author@example.test"
          reviewed_by: %s
          status: %s
        """
        .formatted(quote, reviewer, status);
  }

  private static final String GOOD_QUOTE =
      "An employer shall give a worker written notice of dismissal at least fourteen days before";

  @Test
  void shouldLoadAnActiveRuleWhoseQuoteIsInTheCorpusAndWhoseReviewerIsAnotherPerson() {
    Path file =
        RulesFixtures.write(
            dir, "ok.yaml", rule(GOOD_QUOTE, "active", "\"reviewer@example.test\""));

    RuleLoader.Result result = RuleLoader.load(file, RulesFixtures.corpus(), true);

    assertThat(result.activeRules()).hasSize(1);
    assertThat(result.activeRules().getFirst().sectionKey()).isEqualTo("XX-LAB:9@original");
  }

  @Test
  void shouldStopWhenAnActiveRulesQuoteIsMissingFromTheCorpus() {
    Path file =
        RulesFixtures.write(
            dir,
            "missing.yaml",
            rule(
                "A sentence that the fictional section never contains at all",
                "active",
                "\"reviewer@example.test\""));

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class)
        .hasMessageContaining("xx-notice-min")
        .hasMessageContaining("quote is not found");
  }

  @Test
  void shouldStopWhenAnActiveRuleWasReviewedByItsAuthor() {
    Path file =
        RulesFixtures.write(
            dir, "self.yaml", rule(GOOD_QUOTE, "active", "\"AUTHOR@example.test\""));

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class)
        .hasMessageContaining("reviewed_by");
  }

  @Test
  void shouldStopWhenAnActiveRuleHasNoReviewer() {
    Path file = RulesFixtures.write(dir, "none.yaml", rule(GOOD_QUOTE, "active", "null"));

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class);
  }

  @Test
  void shouldStopWhenTheSectionIsNotACurrentSectionOfTheCorpus() {
    Path file =
        RulesFixtures.write(
            dir,
            "nosection.yaml",
            rule(GOOD_QUOTE, "active", "\"reviewer@example.test\"").replace("\"9\"", "\"404\""));

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class)
        .hasMessageContaining("not a current section");
  }

  @Test
  void shouldOnlyReportAProblemOfADraftRule_andNeverEvaluateIt() {
    Path file =
        RulesFixtures.write(
            dir,
            "draft.yaml",
            rule("A sentence that the fictional section never contains at all", "draft", "null"));

    RuleLoader.Result result = RuleLoader.load(file, RulesFixtures.corpus(), true);

    assertThat(result.rules()).hasSize(1);
    assertThat(result.activeRules()).isEmpty();
    assertThat(result.draftProblems()).singleElement().asString().contains("xx-notice-min");
  }

  @Test
  void shouldRejectAFileThatBreaksTheSchema() {
    Path file =
        RulesFixtures.write(
            dir,
            "schema.yaml",
            rule(GOOD_QUOTE, "active", "\"reviewer@example.test\"").replace("MIN", "ABOUT"));

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class)
        .hasMessageContaining("schema.yaml");
  }

  @Test
  void shouldRejectAUnitThatDoesNotBelongToTheFact() {
    Path file =
        RulesFixtures.write(
            dir,
            "unit.yaml",
            rule(GOOD_QUOTE, "draft", "null").replace("unit: DAYS", "unit: PERCENT"));

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class)
        .hasMessageContaining("not a unit of");
  }

  @Test
  void shouldRejectDuplicateRuleIds() {
    String one = rule(GOOD_QUOTE, "draft", "null");
    Path file = RulesFixtures.write(dir, "dup.yaml", one + one);

    assertThatThrownBy(() -> RuleLoader.load(file, RulesFixtures.corpus(), true))
        .isInstanceOf(RuleLoadException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void shouldNotCheckQuotesBeforeTheFirstCorpusRelease() {
    Path file =
        RulesFixtures.write(
            dir,
            "norelease.yaml",
            rule(
                "A sentence that no corpus has yet at all", "active", "\"reviewer@example.test\""));

    assertThat(RuleLoader.load(file, (act, section) -> Optional.empty(), false).activeRules())
        .hasSize(1);
  }

  @Test
  void shouldTreatAMissingFileAsNoRules() {
    assertThat(RuleLoader.load(dir.resolve("absent.yaml"), RulesFixtures.corpus(), true).rules())
        .isEmpty();
  }

  @Test
  void shouldStopTheApplicationContextFromStarting_whenAnActiveRuleIsInvalid() {
    Path bad =
        RulesFixtures.write(
            dir,
            "bad.yaml",
            rule(
                "Nothing like this is in the section text", "active", "\"reviewer@example.test\""));
    LawReadService law = mock(LawReadService.class);
    when(law.corpusRelease()).thenReturn("2026.11.11-1");
    when(law.section("XX-LAB", "9", null))
        .thenReturn(
            Optional.of(
                new Section(
                    "XX-LAB:9@original",
                    "XX-LAB",
                    null,
                    "Fictional",
                    "9",
                    null,
                    null,
                    null,
                    "en",
                    "An employer shall give a worker written notice of dismissal at least fourteen days.",
                    null,
                    "original",
                    null,
                    true,
                    "https://example.test",
                    Instant.EPOCH,
                    null,
                    List.of(),
                    null)));

    new ApplicationContextRunner()
        .withUserConfiguration(RulesConfiguration.class)
        .withBean(LawReadService.class, () -> law)
        .withPropertyValues("shromikai.rules.files[0]=" + bad)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(
                      context.getStartupFailure().getMessage()
                          + rootMessage(context.getStartupFailure()))
                  .contains("xx-notice-min");
            });
  }

  private static String rootMessage(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage();
  }
}
