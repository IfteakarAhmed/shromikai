package bd.shromikai.api.internal;

import bd.shromikai.analysis.AnalysisFinishedListener;
import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.analysis.AnalysisSubject;
import bd.shromikai.auth.UsageService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Writes the REST usage events of FR-73: one {@code ANALYSIS} event with the run's cost when an
 * analysis succeeds, one {@code COMPARE} event when a compare succeeds. A failed job is not a
 * billable unit and writes nothing. The event carries organisation, kind, units and cost only
 * (R-02).
 */
@Component
final class RestUsageRecorder implements AnalysisFinishedListener {

  private final UsageService usage;

  RestUsageRecorder(UsageService usage) {
    this.usage = usage;
  }

  @Override
  public void finished(AnalysisJob job, boolean compare) {
    AnalysisJob.Outcome outcome = job.outcome();
    if (job.status() != AnalysisJob.Status.ANALYSED || outcome == null) {
      return;
    }
    AnalysisSubject owner = job.owner();
    @Nullable UUID orgId = owner == null ? null : owner.orgId();
    usage.record(
        orgId,
        null,
        null,
        compare ? UsageService.Kind.COMPARE : UsageService.Kind.ANALYSIS,
        1,
        outcome.costUsdMicros());
  }
}
