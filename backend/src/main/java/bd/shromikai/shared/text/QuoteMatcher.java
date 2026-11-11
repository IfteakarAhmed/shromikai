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

  /**
   * The shortest run of whole sentences of {@code displayText} that matches {@code quote} with at
   * least {@code threshold} similarity, taken from the stored text itself so a user only ever sees
   * the authoritative wording, never the model's copy (R-01). Sentences end at a full stop, a danda
   * or a line break. If no run of at most four sentences reaches the threshold (the quote spans a
   * fragment of several), the whole text is returned.
   */
  public static String bestSegment(String quote, String displayText, double threshold) {
    java.util.List<String> sentences = new java.util.ArrayList<>();
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("[^.\\u0964\\u0965\\n]+[.\\u0964\\u0965]?\\s*")
            .matcher(displayText);
    while (m.find()) {
      if (!m.group().isBlank()) {
        sentences.add(m.group());
      }
    }
    for (int run = 1; run <= Math.min(4, sentences.size()); run++) {
      String best = null;
      double bestScore = -1;
      for (int from = 0; from + run <= sentences.size(); from++) {
        String candidate = String.join("", sentences.subList(from, from + run));
        double score = similarity(quote, candidate);
        if (score >= threshold && score > bestScore) {
          best = candidate;
          bestScore = score;
        }
      }
      if (best != null) {
        return best.strip();
      }
    }
    return displayText.strip();
  }
}
