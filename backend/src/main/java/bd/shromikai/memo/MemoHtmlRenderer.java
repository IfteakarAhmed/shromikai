package bd.shromikai.memo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renders the memo of a signed result as one self-contained HTML document (FR-55, T-18). Every
 * value is escaped; there is no script, no remote font, image or style sheet and no link, so the
 * page cannot load or leak anything. Source URLs of citations are shown as text without their
 * scheme. The input is the re-identified result as the owner received it; it exists only in the
 * request's memory (R-02).
 */
@Component
public final class MemoHtmlRenderer {

  private static final String CSS =
      """
      @page { size: A4; margin: 18mm 16mm; }
      body { font-family: "Noto Sans Bengali", "Noto Sans", "Hind Siliguri", sans-serif;
             font-size: 11.5pt; line-height: 1.6; color: #111; }
      h1 { font-size: 20pt; margin: 0 0 4pt; } h2 { font-size: 14pt; margin: 18pt 0 6pt;
             border-bottom: 1px solid #999; } h3 { font-size: 12pt; margin: 12pt 0 4pt; }
      table { border-collapse: collapse; width: 100%; margin: 6pt 0; }
      th, td { border: 1px solid #999; padding: 3pt 5pt; text-align: left; vertical-align: top; }
      .chip { display: inline-block; border: 1px solid #1b5e20; border-radius: 9pt;
              padding: 0 6pt; margin: 2pt 4pt 2pt 0; font-size: 10pt; }
      blockquote { margin: 4pt 0 8pt 10pt; padding-left: 8pt; border-left: 3px solid #bbb; }
      .src { font-size: 9pt; color: #444; } .policy { color: #6a1b9a; } .uncertain { color: #e65100; }
      del { background: #ffcdd2; } ins { background: #c8e6c9; text-decoration: none; }
      footer { margin-top: 18pt; font-size: 9pt; color: #444; border-top: 1px solid #999; }
      """;

  public String render(JsonNode result) {
    JsonNode memo = result.path("memo");
    Index index = new Index(result);
    StringBuilder out = new StringBuilder(8192);
    String lang = memo.path("language").asText("bn");
    out.append("<!doctype html>\n<html lang=\"").append(esc(lang)).append("\">\n<head>\n");
    out.append("<meta charset=\"utf-8\">\n<title>").append(esc(memo.path("title").asText()));
    out.append("</title>\n<style>").append(CSS).append("</style>\n</head>\n<body>\n");
    out.append("<h1>").append(esc(memo.path("title").asText())).append("</h1>\n");
    for (JsonNode section : memo.path("sections")) {
      out.append("<section>\n<h2>").append(esc(section.path("heading").asText())).append("</h2>\n");
      for (JsonNode block : section.path("blocks")) {
        block(out, block, index, lang);
      }
      out.append("</section>\n");
    }
    JsonNode footer = memo.path("footer");
    out.append("<footer>")
        .append(esc(footer.path("analysisId").asText()))
        .append(" · ")
        .append(esc(footer.path("corpusRelease").asText()))
        .append(" · ")
        .append(esc(footer.path("pipelineVersion").asText()))
        .append(" · ")
        .append(esc(footer.path("keyId").asText()))
        .append("</footer>\n</body>\n</html>\n");
    return out.toString();
  }

  private void block(StringBuilder out, JsonNode block, Index index, String lang) {
    switch (block.path("type").asText()) {
      case "paragraph" ->
          out.append("<p>").append(esc(block.path("text").asText())).append("</p>\n");
      case "list" -> {
        out.append("<ul>");
        block
            .path("items")
            .forEach(i -> out.append("<li>").append(esc(i.asText())).append("</li>"));
        out.append("</ul>\n");
      }
      case "riskTable" -> riskTable(out, block, index);
      case "findingRef" -> {
        JsonNode f = index.findings.get(block.path("findingId").asText());
        if (f != null) {
          out.append("<h3>")
              .append(esc(f.path("id").asText()))
              .append(" · ")
              .append(esc(f.path("title").asText()))
              .append(" (")
              .append(esc(f.path("severity").asText()))
              .append(")</h3>\n");
        }
      }
      case "citationChip" -> {
        JsonNode c = index.citations.get(block.path("citationId").asText());
        if (c != null) {
          citation(out, c);
        }
      }
      case "redlineRef" -> {
        JsonNode r = index.redlines.get(block.path("redlineId").asText());
        if (r != null) {
          redline(out, r);
        }
      }
      case "obligationTable" -> obligationTable(out, block, index);
      default -> {
        // unknown block types render nothing
      }
    }
  }

  private void riskTable(StringBuilder out, JsonNode block, Index index) {
    out.append(
        "<table><thead><tr><th>#</th><th>Risk</th><th>Severity</th><th>Status</th></tr></thead><tbody>");
    for (JsonNode id : block.path("findingIds")) {
      JsonNode f = index.findings.get(id.asText());
      if (f == null) {
        continue;
      }
      String status = f.path("verificationStatus").asText();
      String css =
          "POLICY_ONLY".equals(status) ? "policy" : "UNCERTAIN".equals(status) ? "uncertain" : "";
      out.append("<tr><td>")
          .append(esc(f.path("id").asText()))
          .append("</td><td>")
          .append(esc(f.path("title").asText()))
          .append("</td><td>")
          .append(esc(f.path("severity").asText()))
          .append("</td><td class=\"")
          .append(css)
          .append("\">")
          .append(esc(status))
          .append("</td></tr>");
    }
    out.append("</tbody></table>\n");
  }

  private void citation(StringBuilder out, JsonNode c) {
    out.append("<span class=\"chip\">")
        .append(esc(c.path("actCode").asText()))
        .append(" § ")
        .append(esc(c.path("sectionNo").asText()))
        .append("</span>\n<blockquote>")
        .append(esc(c.path("quote").asText()))
        .append("<div class=\"src\">")
        .append(esc(c.path("sectionKey").asText()))
        .append(" · ")
        .append(esc(stripScheme(c.path("sourceUrl").asText())))
        .append("</div></blockquote>\n");
  }

  private void redline(StringBuilder out, JsonNode r) {
    out.append("<h3>").append(esc(r.path("id").asText())).append("</h3>\n<p>");
    for (JsonNode seg : r.path("diff")) {
      String text = esc(seg.path("text").asText());
      switch (seg.path("op").asText()) {
        case "INSERT" -> out.append("<ins>").append(text).append("</ins>");
        case "DELETE" -> out.append("<del>").append(text).append("</del>");
        default -> out.append(text);
      }
    }
    out.append("</p>\n<p>").append(esc(r.path("rationale").asText())).append("</p>\n");
  }

  private void obligationTable(StringBuilder out, JsonNode block, Index index) {
    out.append(
        "<table><thead><tr><th>#</th><th>Party</th><th>Obligation</th><th>When</th><th>Basis</th></tr></thead><tbody>");
    for (JsonNode id : block.path("obligationIds")) {
      JsonNode o = index.obligations.get(id.asText());
      if (o == null) {
        continue;
      }
      JsonNode deadline = o.path("deadline");
      String when =
          (o.path("trigger").isNull() ? "" : o.path("trigger").asText())
              + (deadline.isObject()
                  ? " "
                      + trim(deadline.path("value").asDouble())
                      + " "
                      + deadline.path("unit").asText()
                  : "");
      StringBuilder basis = new StringBuilder(o.path("source").asText());
      for (JsonNode c : o.path("citations")) {
        basis
            .append(" · ")
            .append(c.path("actCode").asText())
            .append(" § ")
            .append(c.path("sectionNo").asText());
      }
      out.append("<tr><td>")
          .append(esc(o.path("id").asText()))
          .append("</td><td>")
          .append(esc(o.path("party").asText()))
          .append("</td><td>")
          .append(esc(o.path("obligation").asText()))
          .append("</td><td>")
          .append(esc(when.trim()))
          .append("</td><td>")
          .append(esc(basis.toString()))
          .append("</td></tr>");
    }
    out.append("</tbody></table>\n");
  }

  private static String trim(double value) {
    return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
  }

  private static String stripScheme(String url) {
    return url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
  }

  static String esc(String text) {
    StringBuilder out = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  /** The result's findings, citations, redlines and obligations by ID. */
  private static final class Index {
    final Map<String, JsonNode> findings = new HashMap<>();
    final Map<String, JsonNode> citations = new HashMap<>();
    final Map<String, JsonNode> redlines = new HashMap<>();
    final Map<String, JsonNode> obligations = new HashMap<>();

    Index(JsonNode result) {
      for (JsonNode f : result.path("findings")) {
        findings.put(f.path("id").asText(), f);
        f.path("citations").forEach(c -> citations.put(c.path("citationId").asText(), c));
      }
      result.path("redlines").forEach(r -> redlines.put(r.path("id").asText(), r));
      result.path("obligations").forEach(o -> obligations.put(o.path("id").asText(), o));
    }
  }
}
