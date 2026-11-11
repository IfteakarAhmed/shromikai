package bd.shromikai.retrieval;

/**
 * Outcome of one embedding backfill run.
 *
 * @param embedded sections embedded in this run
 * @param skipped sections whose stored embedding already matches their content hash
 * @param truncated sections embedded from a cut-off input (above the bge-m3 context limit)
 */
public record BackfillResult(int embedded, int skipped, int truncated) {}
