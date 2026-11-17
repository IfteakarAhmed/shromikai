package bd.shromikai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-81 (gate only): the LLM judge may rate clarity only when it agrees with human raters
 * (evaluation.md §9.5: Spearman ρ ≥ 0.60 and at least 8 of 10 scores within ±1). In P10 the judge
 * was cut (no human ratings, no Gemini), and the report must say so rather than show a score.
 */
@Tag("unit")
@Requirement("FR-81")
class JudgeCalibrationTest {

  @Test
  void shouldNotUseTheJudgeWithoutHumanRatings() {
    JudgeCalibration.Decision d = JudgeCalibration.decide(List.of(), List.of());
    assertThat(d.useJudge()).isFalse();
    assertThat(d.reason()).isEqualTo("no human ratings");
    MetricResult m21 = d.clarityMetric();
    assertThat(m21.status()).isEqualTo(MetricResult.Status.NOT_DONE);
    assertThat(m21.display()).contains("no human ratings");
  }

  @Test
  void shouldUseTheJudgeWhenAgreementPassesBothThresholds() {
    List<Double> human = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 2.0, 3.0, 4.0, 5.0, 1.5);
    List<Double> judge = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 3.0, 3.0, 4.0, 4.0, 2.0);
    JudgeCalibration.Decision d = JudgeCalibration.decide(human, judge);
    assertThat(d.spearman()).isGreaterThanOrEqualTo(0.60);
    assertThat(d.withinOne()).isGreaterThanOrEqualTo(8);
    assertThat(d.useJudge()).isTrue();
  }

  @Test
  void shouldDisableTheJudgeBelowEitherThreshold() {
    List<Double> human = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 1.0, 2.0, 3.0, 4.0, 5.0);
    List<Double> reversed = List.of(5.0, 4.0, 3.0, 2.0, 1.0, 5.0, 4.0, 3.0, 2.0, 1.0);
    JudgeCalibration.Decision d = JudgeCalibration.decide(human, reversed);
    assertThat(d.useJudge()).isFalse();
    assertThat(d.reason()).contains("agreement below threshold");
  }

  @Test
  void shouldComputeSpearmanWithTies() {
    assertThat(JudgeCalibration.spearman(List.of(1.0, 2.0, 3.0), List.of(1.0, 2.0, 3.0)))
        .isEqualTo(1.0);
    assertThat(JudgeCalibration.spearman(List.of(1.0, 2.0, 3.0), List.of(3.0, 2.0, 1.0)))
        .isEqualTo(-1.0);
    assertThat(JudgeCalibration.spearman(List.of(1.0, 1.0, 2.0), List.of(1.0, 2.0, 3.0)))
        .isCloseTo(0.866, org.assertj.core.api.Assertions.within(0.001));
  }
}
