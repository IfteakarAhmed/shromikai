package bd.shromikai.ingest.internal;

import bd.shromikai.shared.text.BanglaNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits cleaned lines into paragraphs and detects the document language (FR-25). A blank line or a
 * line that starts with a numbering marker ({@code ১.}, {@code 1.}, {@code (ক)}, {@code (a)},
 * {@code i.}) begins a new paragraph; other line breaks are joined with a space.
 */
public final class Paragrapher {

  private static final Pattern MARKER =
      Pattern.compile(
          "^\\s*(?:\\d{1,3}[.)]\\s|\\((?:[a-zA-Z]|[ivxIVX]{1,4}|\\d{1,2}|[ক-হ]{1,2}"
              + "|[০-৯]{1,2})\\)\\s|[ivxIVX]{1,4}\\.\\s|[ক-হ][.)]\\s)");

  /** A line with the page it came from (one-based). */
  public record Line(int page, String text) {}

  /** A paragraph with its first page. */
  public record Para(int page, String text) {}

  private Paragrapher() {}

  public static List<Para> split(List<Line> lines) {
    List<Para> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int page = 1;
    for (Line line : lines) {
      String text = line.text().strip();
      if (text.isEmpty()) {
        flush(out, current, page);
        continue;
      }
      if (MARKER.matcher(BanglaNormalizer.toLatinDigits(line.text())).find()
          || MARKER.matcher(line.text()).find()) {
        flush(out, current, page);
      }
      if (current.isEmpty()) {
        page = line.page();
      } else {
        current.append(' ');
      }
      current.append(text);
    }
    flush(out, current, page);
    return out;
  }

  private static void flush(List<Para> out, StringBuilder current, int page) {
    if (!current.isEmpty()) {
      out.add(new Para(page, current.toString()));
      current.setLength(0);
    }
  }

  /** {@code bn} above 70 % Bengali letters, {@code en} below 15 %, else {@code mixed}. */
  public static String language(String text) {
    int bengali = 0;
    int latin = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 'ঀ' && c <= '৿' && Character.isLetter(c)) {
        bengali++;
      } else if (c < 128 && Character.isLetter(c)) {
        latin++;
      }
    }
    int total = bengali + latin;
    if (total == 0) {
      return "en";
    }
    double share = (double) bengali / total;
    return share >= 0.70 ? "bn" : share <= 0.15 ? "en" : "mixed";
  }
}
