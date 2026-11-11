package bd.shromikai.shared.text;

/**
 * Quote matching shared by {@code verify}, {@code rules} and {@code law} (architecture §7.3 check
 * b): both texts are put in {@link BanglaNormalizer#matchingForm matching form}; a substring match
 * scores 1.0, otherwise the score is {@code 1 - d / len(quote)} where {@code d} is the minimum edit
 * distance between the quote and any substring of the text (Sellers' algorithm).
 */
public final class QuoteMatcher {

  private QuoteMatcher() {}

  /**
   * Similarity in [0, 1] of {@code quote} against {@code authoritativeText}; 0 for an empty quote.
   */
  public static double similarity(String quote, String authoritativeText) {
    String q = BanglaNormalizer.matchingForm(quote);
    if (q.isEmpty()) {
      return 0.0;
    }
    String t = BanglaNormalizer.matchingForm(authoritativeText);
    if (t.contains(q)) {
      return 1.0;
    }
    int d = minEditDistance(q, t);
    return Math.max(0.0, 1.0 - (double) d / q.length());
  }

  /** Number of code units of the quote in matching form (used for the 20-character minimum). */
  public static int normalisedLength(String text) {
    return BanglaNormalizer.matchingForm(text).length();
  }

  /** Minimum edit distance between {@code pattern} and any substring of {@code text}. */
  public static int minEditDistance(String pattern, String text) {
    int m = pattern.length();
    int[] column = new int[m + 1];
    for (int i = 0; i <= m; i++) {
      column[i] = i;
    }
    int best = column[m];
    for (int j = 1; j <= text.length(); j++) {
      int diagonal = column[0];
      column[0] = 0; // a match may start anywhere in the text
      for (int i = 1; i <= m; i++) {
        int above = column[i];
        int cost = pattern.charAt(i - 1) == text.charAt(j - 1) ? 0 : 1;
        column[i] = Math.min(Math.min(column[i - 1] + 1, above + 1), diagonal + cost);
        diagonal = above;
      }
      best = Math.min(best, column[m]);
    }
    return best;
  }
}
