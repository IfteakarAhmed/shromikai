package bd.shromikai.privacy;

/** One detected span; offsets index the text that was scanned. Holds no text itself. */
public record PiiMatch(PiiType type, int start, int end) {

  public PiiMatch {
    if (start < 0 || end <= start) {
      throw new IllegalArgumentException("invalid span");
    }
  }

  public int length() {
    return end - start;
  }
}
