package bd.shromikai.law.internal;

import bd.shromikai.shared.Hashing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches and parses one act: the HTML index and print view first, the official PDF only when the
 * HTML is unusable (cannot be fetched, no sections, or no section body) (FR-12). A failure is
 * returned, never filled in (constraint: no law text from memory).
 */
final class ActAcquirer {

  private final PoliteFetcher fetcher;
  private final String baseUrl;

  ActAcquirer(PoliteFetcher fetcher, String baseUrl) {
    this.fetcher = fetcher;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  AcquiredAct acquire(ActDefinition def) {
    Instant now = Instant.now();
    if (def.portalActId() == null) {
      if (def.pdfUrl() == null) {
        return failed(def, now, List.of(), "NOT_PUBLISHED_ON_PORTAL: pack lists no portal act id");
      }
      return fromPdf(def, now, List.of(), "no portal act id");
    }
    String indexUrl = baseUrl + "/act-" + def.portalActId() + ".html";
    String printUrl = baseUrl + "/act-print-" + def.portalActId() + ".html";
    List<String> urls = new ArrayList<>(List.of(indexUrl, printUrl));
    String htmlProblem;
    try {
      FetchedPage index = fetcher.fetch(indexUrl);
      FetchedPage print = fetcher.fetch(printUrl);
      ParsedAct parsed = new ActParser(baseUrl).parse(index.text(), print.text());
      if (usable(parsed)) {
        return new AcquiredAct(
            def,
            parsed,
            AcquiredAct.SourceKind.HTML,
            Hashing.sha256Hex(index.sha256() + ":" + print.sha256()),
            print.fetchedAt(),
            urls,
            null);
      }
      htmlProblem =
          parsed.problems().isEmpty() ? "no section has a body" : parsed.problems().get(0);
    } catch (FetchException e) {
      htmlProblem = e.getMessage();
    }
    if (def.pdfUrl() == null) {
      return failed(def, now, urls, "HTML_UNUSABLE: " + htmlProblem);
    }
    return fromPdf(def, now, urls, htmlProblem);
  }

  private AcquiredAct fromPdf(ActDefinition def, Instant now, List<String> urls, String why) {
    List<String> all = new ArrayList<>(urls);
    all.add(def.pdfUrl());
    try {
      FetchedPage pdf = fetcher.fetch(def.pdfUrl());
      ParsedAct parsed = PdfActParser.parse(pdf.bytes(), def.pdfUrl());
      if (!parsed.sections().isEmpty()) {
        return new AcquiredAct(
            def, parsed, AcquiredAct.SourceKind.PDF, pdf.sha256(), pdf.fetchedAt(), all, null);
      }
      return failed(def, now, all, "PDF_UNUSABLE after HTML problem (" + why + ")");
    } catch (FetchException | java.io.UncheckedIOException e) {
      return failed(
          def, now, all, "PDF_FAILED after HTML problem (" + why + "): " + e.getMessage());
    }
  }

  private static boolean usable(ParsedAct act) {
    return !act.sections().isEmpty() && act.sections().stream().anyMatch(s -> !s.text().isBlank());
  }

  private static AcquiredAct failed(ActDefinition def, Instant now, List<String> urls, String why) {
    return new AcquiredAct(def, null, AcquiredAct.SourceKind.NONE, null, now, urls, why);
  }
}
