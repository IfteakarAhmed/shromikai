package bd.shromikai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.ShownCitation;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-80, R-07: results are append-only, so a case that was run again (for example after a harness
 * failure) appears twice. The report counts the latest run of each contract, system and side, and
 * lists the superseded run under failures instead of dropping it silently.
 */
@Tag("unit")
@Requirement("FR-80")
class ReportAssemblerTest {

  @Test
  void shouldCountTheLatestRunAndListTheSupersededOne() {
    AnalysisCase failed =
        AnalysisCase.builder("syn-xx-01", "shromikai", "WORKER")
            .outcome("FAILED")
            .failureCode("internal-error")
            .build();
    AnalysisCase rerun =
        AnalysisCase.builder("syn-xx-01", "shromikai", "WORKER")
            .shown(List.of(new ShownCitation("a", "XX-LAB:9@original", "VERIFIED")))
            .build();
    EvalResults.Bundle results =
        new EvalResults.Bundle(List.of(failed, rerun), List.of(), List.of());

    EvalReport report =
        ReportAssembler.assemble(ReportAssembler.Inputs.forSmoke(results, "r", "toy"));

    MetricResult m01 = report.metricsBySystem().get("shromikai").get("M-01");
    assertThat(m01.n()).isEqualTo(1);
    assertThat(report.failures())
        .anyMatch(
            f ->
                f.contains("superseded")
                    && f.contains("syn-xx-01")
                    && f.contains("internal-error"));
    assertThat(report.failures()).noneMatch(f -> f.startsWith("syn-xx-01 / shromikai: FAILED"));
  }
}
