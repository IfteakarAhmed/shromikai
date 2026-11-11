package bd.shromikai.law.internal;

import bd.shromikai.law.SectionNumbers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Fallback reader for the official PDF of an act when the HTML is unusable (FR-12). It splits the
 * extracted text at lines that start with a printed section number; it knows no chapters and no
 * footnotes, so the quality report names the source kind of every act.
 */
final class PdfActParser {

  static final String PARSER_VERSION = "bdlaws-pdf-1";

  private static final Pattern START =
      Pattern.compile("^\\s*([0-9০-৯]+[A-Za-zক-হ]*(?:-[0-9০-৯]+[A-Za-zক-হ]*)?)\\s*[.।৷]\\s+(.*)$");

  private PdfActParser() {}

  static ParsedAct parse(byte[] pdf, String sourceUrl) {
    String text;
    try (PDDocument document = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<String> problems = new ArrayList<>();
    List<ParsedSection> sections = new ArrayList<>();
    String title = "";
    ParsedSection.Builder open = null;
    for (String raw : text.split("\\R")) {
      String line = ActParser.collapse(raw);
      if (line.isEmpty()) {
        continue;
      }
      Matcher start = START.matcher(line);
      Optional<String> latin =
          start.matches() ? SectionNumbers.toLatin(start.group(1)) : Optional.empty();
      if (latin.isPresent()) {
        if (open != null) {
          sections.add(open.build());
        }
        open =
            new ParsedSection.Builder(
                sections.size(),
                latin.get(),
                start.group(1),
                start.group(2),
                line,
                "",
                "",
                sourceUrl,
                List.of());
      } else if (open != null) {
        open.appendLine(line);
      } else if (title.isEmpty()) {
        title = line;
      }
    }
    if (open != null) {
      sections.add(open.build());
    }
    if (sections.isEmpty()) {
      problems.add("PDF_NO_SECTIONS: no line starts with a section number");
    }
    return new ParsedAct(title, "", null, sections, Map.of(), problems);
  }
}
