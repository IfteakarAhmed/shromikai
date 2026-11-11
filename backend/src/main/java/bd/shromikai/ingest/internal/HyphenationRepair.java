package bd.shromikai.ingest.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Joins words that a line break split with a hyphen (FR-25): a line ending in a letter and "-"
 * followed by a line that starts with a lower-case letter becomes one word without the hyphen.
 * Hyphens before digits, capitals or spaces stay.
 */
public final class HyphenationRepair {

  private static final Pattern ENDS_WITH_SPLIT = Pattern.compile(".*\\p{L}-$");

  private HyphenationRepair() {}

  public static List<String> repair(List<String> lines) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (i + 1 < lines.size()
          && ENDS_WITH_SPLIT.matcher(line.stripTrailing()).matches()
          && startsWithLower(lines.get(i + 1))) {
        String stem = line.stripTrailing();
        out.add(stem.substring(0, stem.length() - 1) + lines.get(i + 1).stripLeading());
        i++;
        // The merged line may itself end with a hyphen; handle by re-checking the merged line.
        while (i + 1 < lines.size()
            && ENDS_WITH_SPLIT.matcher(out.getLast()).matches()
            && startsWithLower(lines.get(i + 1))) {
          String merged = out.removeLast();
          out.add(merged.substring(0, merged.length() - 1) + lines.get(i + 1).stripLeading());
          i++;
        }
      } else {
        out.add(line);
      }
    }
    return out;
  }

  private static boolean startsWithLower(String line) {
    String s = line.stripLeading();
    return !s.isEmpty() && Character.isLowerCase(s.codePointAt(0));
  }
}
