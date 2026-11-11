package bd.shromikai.memo;

import java.util.Arrays;

/**
 * A cheap reading-level check for memo narrative (FR-51): the average number of words per sentence.
 * Sentences end at {@code ।}, {@code .}, {@code ?} or {@code !}. It does not measure clarity
 * (M-21).
 */
public final class PlainLanguageLint {

  /** The FR-51 limit for the average sentence. */
  public static final double MAX_AVERAGE_WORDS = 20;

  private PlainLanguageLint() {}

  public static double averageSentenceWords(String text) {
    String[] sentences = text.split("[।.?!]+");
    long count = Arrays.stream(sentences).filter(s -> !s.isBlank()).count();
    if (count == 0) {
      return 0;
    }
    long words =
        Arrays.stream(sentences)
            .filter(s -> !s.isBlank())
            .mapToLong(s -> s.trim().split("\\s+").length)
            .sum();
    return (double) words / count;
  }
}
