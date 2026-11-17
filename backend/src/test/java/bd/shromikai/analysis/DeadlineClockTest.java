package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * NFR-01 and architecture 5.3 (pipeline wall-time limit): the analysis deadline is measured on the
 * injected clock, not on the wall clock. A test clock fixed far from today must not make the 30 s
 * limit expire at once (or never), so the result never depends on the date the suite runs.
 */
@Tag("unit")
@Requirement("NFR-01")
class DeadlineClockTest {

  private static final AnalysisSubject OWNER =
      new AnalysisSubject("USER", Hashing.sha256Hex("owner"), null, "API");

  private static AnalysisJob.Status runWith(Instant fixedNow) {
    PipelineHarness harness =
        new PipelineHarness(
            null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(AnalysisFixtures.noticeMinRule()),
            PipelineHarness.properties(),
            Clock.fixed(fixedNow, ZoneOffset.UTC));
    AnalysisJob job = harness.classifiedJob();
    harness.pipeline.run(job, AnalysisRequest.of(Side.WORKER), OWNER);
    return job.status();
  }

  @Test
  void aClockFarInThePastDoesNotExpireTheDeadline() {
    assertThat(runWith(Instant.parse("2020-01-01T00:00:00Z")))
        .isEqualTo(AnalysisJob.Status.ANALYSED);
  }

  @Test
  void aClockFarInTheFutureDoesNotDisableTheDeadlineEither() {
    assertThat(runWith(Instant.parse("2040-01-01T00:00:00Z")))
        .isEqualTo(AnalysisJob.Status.ANALYSED);
  }
}
