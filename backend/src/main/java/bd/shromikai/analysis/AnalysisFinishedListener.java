package bd.shromikai.analysis;

/**
 * Told once when an analysis or compare job has ended in any way (FR-73). Listeners belong to other
 * modules, for example usage metering in {@code api}; a listener must not throw and must never
 * store content (R-02). A failure in a listener is logged by class name only and never fails the
 * job.
 */
public interface AnalysisFinishedListener {

  /**
   * @param job the ended job; read {@link AnalysisJob#status()} to tell success from failure
   * @param compare true when the job was started as compare mode
   */
  void finished(AnalysisJob job, boolean compare);
}
