package bd.shromikai.law.internal;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The outcome of acquiring one act: parsed sections and where they came from, or a failure. */
record AcquiredAct(
    ActDefinition definition,
    @Nullable ParsedAct act,
    SourceKind sourceKind,
    @Nullable String sourceSha256,
    Instant fetchedAt,
    List<String> sourceUrls,
    @Nullable String failure) {

  enum SourceKind {
    HTML,
    PDF,
    NONE
  }

  boolean ok() {
    return failure == null && act != null;
  }

  String parserVersion() {
    return sourceKind == SourceKind.PDF ? PdfActParser.PARSER_VERSION : ActParser.PARSER_VERSION;
  }
}
