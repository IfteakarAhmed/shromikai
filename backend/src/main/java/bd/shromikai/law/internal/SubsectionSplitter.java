package bd.shromikai.law.internal;

import bd.shromikai.law.SectionNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the numbered paragraphs ("(১)", "(2A)") of a section text, one per line (FR-13). */
final class SubsectionSplitter {

  private static final Pattern START =
      Pattern.compile(
          "^\\s*\\[?\\s*(?:[0-9০-৯][^\\s]*\\s*[.।৷]\\s*)?\\(([0-9০-৯]+[A-Za-zক-হ]*)\\)");

  private SubsectionSplitter() {}

  static List<ParsedSection.Subsection> split(String text) {
    List<ParsedSection.Subsection> out = new ArrayList<>();
    for (String line : text.split("\n")) {
      Matcher matcher = START.matcher(line);
      if (matcher.find() && SectionNumbers.toLatin(matcher.group(1)).isPresent()) {
        out.add(new ParsedSection.Subsection("(" + matcher.group(1) + ")", line.strip()));
      }
    }
    return out;
  }
}
