package bd.shromikai.ingest;

/**
 * What kind of input an analysis started from (docs/spec/api.md, {@code analysis_run.input_kind}).
 */
public enum InputKind {
  PDF_TEXT,
  PDF_SCAN,
  IMAGE,
  TEXT
}
