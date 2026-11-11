package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.ClaimedCitation;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-49: merging rule and LLM findings (architecture §9.3). */
@Tag("unit")
@Requirement("FR-49")
class FindingMergeTest {

  private static final String KEY = "XV-LAB:9@original";
  private final FindingMerge merge = new FindingMerge();

  private static ClaimedCitation claim(String key, bd.shromikai.verify.CitationOrigin origin) {
    return origin == bd.shromikai.verify.CitationOrigin.RULE
        ? ClaimedCitation.of(
            key,
            "shall give a worker written notice of dismissal",
            SensitiveText.of("rule claim long enough"),
            "en",
            origin,
            "xx-notice-min",
            new bd.shromikai.verify.Provenance(
                "xx-notice-min", "a@example.test", "b@example.test", true))
        : ClaimedCitation.llm(
            key,
            "shall give a worker written notice of dismissal",
            SensitiveText.of("llm claim long enough"),
            "en");
  }

  private static Finding finding(
      FindingSource source,
      FindingKind kind,
      Severity severity,
      double confidence,
      List<String> clauses,
      String clauseType,
      List<ClaimedCitation> claims,
      String explanation) {
    return new Finding(
        "x",
        SensitiveText.of("A title of a finding"),
        kind,
        severity,
        kind == FindingKind.OMISSION,
        source,
        clauseType,
        clauses,
        SensitiveText.of(explanation),
        SensitiveText.of("why"),
        confidence,
        source == FindingSource.RULE_ENGINE ? "xx-notice-min" : null,
        claims,
        FindingVerification.UNCERTAIN,
        List.of(),
        List.of(),
        null,
        null);
  }

  private static Finding rule(Severity severity, double confidence) {
    return finding(
        FindingSource.RULE_ENGINE,
        FindingKind.CONFLICT,
        severity,
        confidence,
        List.of("c4"),
        "NOTICE_AND_RESIGNATION",
        List.of(claim(KEY, bd.shromikai.verify.CitationOrigin.RULE)),
        "rule explanation");
  }

  private static Finding llm(
      Severity severity, List<String> clauses, String key, String explanation) {
    return finding(
        FindingSource.LLM_ANALYSIS,
        FindingKind.CONFLICT,
        severity,
        0.7,
        clauses,
        "NOTICE_AND_RESIGNATION",
        key == null ? List.of() : List.of(claim(key, bd.shromikai.verify.CitationOrigin.LLM)),
        explanation);
  }

  @Test
  void shouldMergeAgreeingFindingsIntoOneRuleFindingWithMoreConfidence() {
    FindingMerge.Result result =
        merge.merge(
            List.of(rule(Severity.HIGH, 0.85)),
            List.of(llm(Severity.HIGH, List.of("c4"), KEY, "plain words")));

    assertThat(result.findings()).hasSize(1);
    Finding merged = result.findings().getFirst();
    assertThat(merged.source()).isEqualTo(FindingSource.RULE_ENGINE);
    assertThat(merged.confidence()).isEqualTo(0.95, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(merged.explanation().reveal()).isEqualTo("plain words");
    assertThat(merged.claims())
        .singleElement()
        .satisfies(c -> assertThat(c.origin()).isEqualTo(bd.shromikai.verify.CitationOrigin.RULE));
    assertThat(merged.ruleId()).isEqualTo("xx-notice-min");
    assertThat(result.conflicts()).isEmpty();
  }

  @Test
  void shouldCapTheConfidenceAtOne() {
    Finding merged =
        merge
            .merge(
                List.of(rule(Severity.HIGH, 0.97)),
                List.of(llm(Severity.HIGH, List.of("c4"), KEY, "x")))
            .findings()
            .getFirst();

    assertThat(merged.confidence()).isEqualTo(1.0);
  }

  @Test
  void shouldKeepTheRulesSeverityAndFlagADisagreement() {
    FindingMerge.Result result =
        merge.merge(
            List.of(rule(Severity.CRITICAL, 0.85)),
            List.of(llm(Severity.MEDIUM, List.of("c4"), KEY, "x")));

    assertThat(result.findings().getFirst().severity()).isEqualTo(Severity.CRITICAL);
    assertThat(result.conflicts())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.findingId()).isEqualTo("f1");
              assertThat(c.ruleSeverity()).isEqualTo(Severity.CRITICAL);
              assertThat(c.llmSeverity()).isEqualTo(Severity.MEDIUM);
            });
  }

  @Test
  void shouldNotMergeFindingsOnDifferentClausesOrSections() {
    FindingMerge.Result result =
        merge.merge(
            List.of(rule(Severity.HIGH, 0.85)),
            List.of(
                llm(Severity.HIGH, List.of("c7"), KEY, "other clause"),
                llm(Severity.HIGH, List.of("c4"), "XV-LAB:12@original", "other section")));

    assertThat(result.findings()).hasSize(3);
  }

  @Test
  void shouldDeduplicateRepeatedLlmFindingsAndKeepTheMoreSevere() {
    FindingMerge.Result result =
        merge.merge(
            List.of(),
            List.of(
                llm(Severity.MEDIUM, List.of("c4"), KEY, "first"),
                llm(Severity.HIGH, List.of("c4"), KEY, "second")));

    assertThat(result.findings())
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(Severity.HIGH));
  }

  @Test
  void shouldMergeAnOmissionRuleFindingWithAnLlmOmissionOfTheSameTopic() {
    Finding omissionRule =
        finding(
            FindingSource.RULE_ENGINE,
            FindingKind.OMISSION,
            Severity.MEDIUM,
            0.85,
            List.of(),
            "LEAVE",
            List.of(claim(KEY, bd.shromikai.verify.CitationOrigin.RULE)),
            "rule says leave is missing");
    Finding omissionLlm =
        finding(
            FindingSource.LLM_ANALYSIS,
            FindingKind.OMISSION,
            Severity.MEDIUM,
            0.7,
            List.of(),
            "LEAVE",
            List.of(),
            "no leave clause");

    FindingMerge.Result result = merge.merge(List.of(omissionRule), List.of(omissionLlm));

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().getFirst().explanation().reveal()).isEqualTo("no leave clause");
  }

  @Test
  void shouldNumberFindingsBySeverityWithRulesFirst() {
    FindingMerge.Result result =
        merge.merge(
            List.of(rule(Severity.HIGH, 0.85)),
            List.of(
                llm(Severity.LOW, List.of("c1"), "XV-LAB:12@original", "low"),
                llm(Severity.CRITICAL, List.of("c2"), "XV-LAB:13@original", "critical")));

    assertThat(result.findings()).extracting(Finding::id).containsExactly("f1", "f2", "f3");
    assertThat(result.findings())
        .extracting(Finding::severity)
        .containsExactly(Severity.CRITICAL, Severity.HIGH, Severity.LOW);
  }
}
