package bd.shromikai.verify;

/** Outcome of one verification check (api.md §5.6). */
public enum CheckOutcome {
  PASS,
  FAIL,
  SKIPPED,
  ERROR
}
