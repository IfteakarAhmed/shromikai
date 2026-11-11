package bd.shromikai.ingest.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes repeated page headers and footers (FR-25). A line among the first or last two non-blank
 * lines of a page that, with digits masked, recurs on at least three pages is kept once (on the
 * first page it appears) and dropped elsewhere. Bare page numbers ("Page 2 of 5", "পৃষ্ঠা ৩") are
 * dropped everywhere.
 */
public final class HeaderFooterRemover {

  static final int MIN_PAGES = 3;
  private static final int EDGE = 2;
  private static final Pattern PAGE_NUMBER =
      Pattern.compile(
          "^(?:(?:page|পৃষ্ঠা|পাতা)\\s*)?#(?:\\s*(?:of|/|এর)\\s*#)?$", Pattern.CASE_INSENSITIVE);

  private HeaderFooterRemover() {}

  /** Returns the pages with repeated edge lines removed; the input is not modified. */
  public static List<List<String>> remove(List<List<String>> pages) {
    Map<String, Integer> pagesWithKey = new HashMap<>();
    for (List<String> page : pages) {
      Set<String> seen = new HashSet<>();
      for (int index : edgeIndexes(page)) {
        seen.add(key(page.get(index)));
      }
      seen.forEach(k -> pagesWithKey.merge(k, 1, Integer::sum));
    }
    Set<String> repeated = new HashSet<>();
    pagesWithKey.forEach(
        (k, n) -> {
          if (pages.size() >= MIN_PAGES && n >= MIN_PAGES && !k.isEmpty()) {
            repeated.add(k);
          }
        });
    Set<String> alreadyKept = new HashSet<>();
    List<List<String>> out = new ArrayList<>();
    for (List<String> page : pages) {
      Set<Integer> edges = new HashSet<>(edgeIndexes(page));
      List<String> kept = new ArrayList<>();
      for (int i = 0; i < page.size(); i++) {
        String line = page.get(i);
        String k = key(line);
        if (!line.isBlank() && PAGE_NUMBER.matcher(k).matches() && edges.contains(i)) {
          continue;
        }
        if (edges.contains(i) && repeated.contains(k) && !alreadyKept.add(k)) {
          continue;
        }
        kept.add(line);
      }
      out.add(kept);
    }
    return out;
  }

  private static List<Integer> edgeIndexes(List<String> page) {
    List<Integer> nonBlank = new ArrayList<>();
    for (int i = 0; i < page.size(); i++) {
      if (!page.get(i).isBlank()) {
        nonBlank.add(i);
      }
    }
    List<Integer> edges = new ArrayList<>();
    for (int i = 0; i < nonBlank.size(); i++) {
      if (i < EDGE || i >= nonBlank.size() - EDGE) {
        edges.add(nonBlank.get(i));
      }
    }
    return edges;
  }

  /** Digits (Latin or Bengali) masked, whitespace collapsed, lower case. */
  static String key(String line) {
    return line.strip()
        .replaceAll("[0-9০-৯]+", "#")
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }
}
