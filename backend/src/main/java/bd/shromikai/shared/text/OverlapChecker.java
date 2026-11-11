package bd.shromikai.shared.text;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

/**
 * The 8-token rule of the glossary (AGENTS.md §2): text that shares {@value #WINDOW} or more
 * consecutive tokens with a source document counts as contract text. A token is a whitespace
 * separated word after Unicode NFC. Windows are compared in two forms, the raw tokens (case
 * sensitive) and the {@link BanglaNormalizer#matchingForm matching form} (case, punctuation and
 * digit script folded), so a copied span is found even when punctuation or case differs.
 */
public final class OverlapChecker {

  public static final int WINDOW = 8;

  private final Set<String> rawWindows = new HashSet<>();
  private final Set<String> foldedWindows = new HashSet<>();

  /** Indexes every 8-token window of the sources. */
  public OverlapChecker(Iterable<String> sources) {
    for (String source : sources) {
      index(rawTokens(source), rawWindows);
      index(foldedTokens(source), foldedWindows);
    }
  }

  public static OverlapChecker of(String... sources) {
    return new OverlapChecker(java.util.List.of(sources));
  }

  /**
   * Whether {@code candidate} shares at least {@value #WINDOW} consecutive tokens with a source.
   */
  public boolean overlaps(String candidate) {
    return hits(rawTokens(candidate), rawWindows) || hits(foldedTokens(candidate), foldedWindows);
  }

  private static void index(String[] tokens, Set<String> windows) {
    for (int i = 0; i + WINDOW <= tokens.length; i++) {
      windows.add(window(tokens, i));
    }
  }

  private static boolean hits(String[] tokens, Set<String> windows) {
    for (int i = 0; i + WINDOW <= tokens.length; i++) {
      if (windows.contains(window(tokens, i))) {
        return true;
      }
    }
    return false;
  }

  private static String window(String[] tokens, int from) {
    return String.join(" ", java.util.Arrays.copyOfRange(tokens, from, from + WINDOW));
  }

  private static String[] rawTokens(String text) {
    String nfc = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
    return nfc.isEmpty() ? new String[0] : nfc.split("\\s+");
  }

  private static String[] foldedTokens(String text) {
    String folded = BanglaNormalizer.matchingForm(text);
    return folded.isEmpty() ? new String[0] : folded.split(" ");
  }
}
