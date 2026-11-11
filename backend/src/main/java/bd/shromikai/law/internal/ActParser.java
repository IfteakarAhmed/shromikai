package bd.shromikai.law.internal;

import bd.shromikai.law.SectionNumbers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * Parses an act from the two portal pages that list the same sections in the same order: the act
 * index ({@code /act-{id}.html}: number and heading of each section) and the print view ({@code
 * /act-print-{id}.html}: chapter, text and footnotes). If the counts differ the act is reported as
 * failed (no sections), never guessed (FR-13, ADR-0011 decision 3).
 */
final class ActParser {

  static final String PARSER_VERSION = "bdlaws-print-1";

  private final String baseUrl;

  ActParser(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  ParsedAct parse(String indexHtml, String printHtml) {
    Document index = Jsoup.parse(indexHtml);
    Document print = Jsoup.parse(printHtml);
    List<String> problems = new ArrayList<>();

    Element header = index.selectFirst("h3");
    String title = header == null ? "" : textWithoutFootnotes(header);
    Element numberElement = index.selectFirst("h4");
    String number = numberElement == null ? "" : collapse(numberElement.text());
    Element publish = index.selectFirst(".publish-date");
    var published =
        publish == null
            ? java.util.Optional.<java.time.LocalDate>empty()
            : BanglaDates.parse(publish.text());

    List<Element> entries = index.select("p.act-section-name a[href]");
    List<Element> rows = print.select("div.lineremoves");
    if (entries.size() != rows.size() || entries.isEmpty()) {
      problems.add(
          "COUNT_MISMATCH: index lists "
              + entries.size()
              + " sections, print view has "
              + rows.size()
              + " rows");
      return new ParsedAct(title, number, published.orElse(null), List.of(), Map.of(), problems);
    }

    Map<String, String> amendingActs = new LinkedHashMap<>();
    List<ParsedSection> sections = new ArrayList<>();
    String chapterNo = "";
    String chapterName = "";
    ParsedSection.Builder open = null;
    for (int i = 0; i < rows.size(); i++) {
      Element row = rows.get(i);
      Element chapter = row.selectFirst(".act-chapter-group");
      if (chapter != null) {
        Element no = chapter.selectFirst(".act-chapter-no");
        Element name = chapter.selectFirst(".act-chapter-name");
        chapterNo = no == null ? "" : collapse(no.text());
        chapterName = name == null ? "" : collapse(name.text());
      }
      String indexTitle = collapse(entries.get(i).text());
      String url = baseUrl + entries.get(i).attr("href");
      Element details = row.selectFirst(".txt-details");
      String text = details == null ? "" : displayText(details);
      List<AmendmentNote> notes = notes(row, amendingActs);
      java.util.Optional<String> printed = SectionNumbers.leadingNumber(indexTitle);
      java.util.Optional<String> latin = printed.flatMap(SectionNumbers::toLatin);
      if (printed.isPresent() && latin.isEmpty()) {
        problems.add("UNMAPPABLE_NUMBER: " + printed.get());
      }
      if (latin.isPresent()) {
        if (open != null) {
          sections.add(open.build());
        }
        open =
            new ParsedSection.Builder(
                sections.size(),
                latin.get(),
                printed.get(),
                SectionNumbers.withoutLeadingNumber(indexTitle),
                text,
                chapterNo,
                chapterName,
                url,
                notes);
      } else if (open != null) {
        // A further headed paragraph of the previous section (ADR-0011 decision 3).
        Element head = row.selectFirst(".txt-head");
        open.append(head == null ? indexTitle : collapse(head.text()), text, notes);
      } else {
        problems.add("UNNUMBERED_FIRST_ROW: " + indexTitle);
      }
    }
    if (open != null) {
      sections.add(open.build());
    }
    return new ParsedAct(title, number, published.orElse(null), sections, amendingActs, problems);
  }

  private List<AmendmentNote> notes(Element row, Map<String, String> amendingActs) {
    List<AmendmentNote> notes = new ArrayList<>();
    for (Element item : row.select("li.footnoteList")) {
      Element copy = item.clone();
      copy.select("h6").remove();
      notes.add(AmendmentParser.note(copy.text()));
    }
    if (notes.isEmpty()) {
      for (Element footnote : row.select("span.footnote[title]")) {
        notes.add(AmendmentParser.note(footnote.attr("title")));
      }
    }
    for (AmendmentNote note : notes) {
      if (note.amendingActId() != null) {
        amendingActs.putIfAbsent(note.amendingActId(), note.amendingActTitle());
      }
    }
    return notes;
  }

  /**
   * Paragraphs one per line (spacer divs end a line); footnote markers removed; wording untouched.
   */
  static String displayText(Element details) {
    Element copy = details.clone();
    copy.select("span.footnote").remove();
    copy.select("p, br, div").forEach(e -> e.after(new TextNode("\n")));
    List<String> lines = new ArrayList<>();
    for (String line : copy.wholeText().split("\n")) {
      String collapsed = collapse(line);
      if (!collapsed.isEmpty()) {
        lines.add(collapsed);
      }
    }
    return String.join("\n", lines);
  }

  private static String textWithoutFootnotes(Element element) {
    Element copy = element.clone();
    copy.select("span.footnote").remove();
    return collapse(copy.text());
  }

  static String collapse(String text) {
    return text.replace(' ', ' ').replaceAll("\\s+", " ").strip();
  }
}
