package bd.shromikai.retrieval;

/**
 * How the full-text query is built (ADR-0013).
 *
 * @param maxDocFreq drop query words that occur in more than this share of the current sections
 *     (1.0 keeps every word); a floor on the absolute count keeps tiny corpora unfiltered
 * @param languageFilter match only sections whose authoritative language is the script of the
 *     query: a lexical match cannot cross from English to Bangla
 */
public record TextTuning(double maxDocFreq, boolean languageFilter) {

  /** The untuned P4 behaviour: every word, every language. */
  public static final TextTuning OFF = new TextTuning(1.0, false);

  public TextTuning {
    if (maxDocFreq <= 0 || maxDocFreq > 1) {
      throw new IllegalArgumentException("maxDocFreq must be in (0, 1]");
    }
  }
}
