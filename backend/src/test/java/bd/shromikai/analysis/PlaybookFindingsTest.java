package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.FactParameter;
import bd.shromikai.rules.Playbook;
import bd.shromikai.rules.PlaybookRule;
import bd.shromikai.rules.RuleComparator;
import bd.shromikai.rules.Severity;
import bd.shromikai.rules.Unit;
import bd.shromikai.testsupport.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-57: playbook deviations are policy findings, never presented as legal findings. */
@Tag("unit")
@Requirement("FR-57")
class PlaybookFindingsTest {

  /** The harness contract states "7 days" notice by the employer (clause c1). */
  private static Playbook playbookWanting14DaysNotice() {
    PlaybookRule rule =
        new PlaybookRule(
            "NOTICE_AND_RESIGNATION",
            FactParameter.NOTICE_BY_EMPLOYER,
            RuleComparator.MIN,
            30.0,
            Unit.DAYS,
            Severity.MEDIUM,
            "আমাদের নীতিতে নোটিস কমপক্ষে ৩০ দিন।",
            "Our policy gives at least 30 days notice.",
            null);
    return new Playbook(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Standard",
        "EMPLOYER",
        1,
        Instant.parse("2026-11-16T10:00:00Z"),
        null,
        List.of(rule));
  }

  private static AnalysisRequest withPlaybook() {
    return new AnalysisRequest(Side.EMPLOYER, "en", Map.of(), playbookWanting14DaysNotice());
  }

  @Test
  void aDeviationBecomesAPolicyFindingSeparateFromTheLegalOnes() {
    AnalysisJob job = PipelineHarness.standard().run(withPlaybook());

    List<Finding> policy =
        job.outcome().findings().stream()
            .filter(f -> f.source() == FindingSource.PLAYBOOK)
            .toList();
    assertThat(policy).hasSize(1);
    Finding deviation = policy.get(0);
    assertThat(deviation.verification()).isEqualTo(FindingVerification.POLICY_ONLY);
    assertThat(deviation.citations()).isEmpty();
    assertThat(deviation.kind()).isEqualTo(FindingKind.POLICY_DEVIATION);
    assertThat(deviation.explanation().reveal())
        .isEqualTo("Our policy gives at least 30 days notice.");
    assertThat(deviation.clauseIds()).containsExactly("c1");
  }

  @Test
  void policyFindingsNeverGoThroughTheVerifierTheCorrectiveLoopOrCounterOffers() {
    PipelineHarness h = PipelineHarness.standard();

    AnalysisJob job = h.run(withPlaybook());

    // legal findings are still VERIFIED, with a redline; the policy one has none
    List<Finding> legal =
        job.outcome().findings().stream()
            .filter(f -> f.source() != FindingSource.PLAYBOOK)
            .toList();
    assertThat(legal).isNotEmpty();
    assertThat(legal)
        .allSatisfy(
            f -> assertThat(f.verification()).isNotEqualTo(FindingVerification.POLICY_ONLY));
    Finding policy =
        job.outcome().findings().stream()
            .filter(f -> f.source() == FindingSource.PLAYBOOK)
            .findFirst()
            .orElseThrow();
    assertThat(policy.redlineId()).isNull();
    assertThat(policy.reviewItemId()).isNull();
    // numbering continues after the legal findings
    assertThat(policy.id()).isEqualTo("f" + (legal.size() + 1));
  }

  @Test
  void withoutAPlaybookThereIsNoPolicyFinding() {
    AnalysisJob job = PipelineHarness.standard().run();

    assertThat(job.outcome().findings()).noneMatch(f -> f.source() == FindingSource.PLAYBOOK);
  }

  @Test
  void aFindingWithTheBanglaOutputUsesTheBanglaMessage() {
    AnalysisRequest bangla =
        new AnalysisRequest(Side.EMPLOYER, "bn", Map.of(), playbookWanting14DaysNotice());

    AnalysisJob job = PipelineHarness.standard().run(bangla);

    assertThat(
            job.outcome().findings().stream()
                .filter(f -> f.source() == FindingSource.PLAYBOOK)
                .findFirst()
                .orElseThrow()
                .explanation()
                .reveal())
        .isEqualTo("আমাদের নীতিতে নোটিস কমপক্ষে ৩০ দিন।");
  }
}
