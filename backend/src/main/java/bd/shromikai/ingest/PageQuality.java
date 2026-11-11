package bd.shromikai.ingest;

import org.jspecify.annotations.Nullable;

/**
 * How one page was read. OCR quality is reported, never hidden (P5 constraint, FR-27).
 *
 * @param ocrConfidence mean word confidence 0 to 100, {@code null} when the text layer was used
 */
public record PageQuality(int page, String method, @Nullable Double ocrConfidence) {

  public static final String TEXT_LAYER = "TEXT_LAYER";
  public static final String LEGACY_FONT = "LEGACY_FONT";
  public static final String OCR = "OCR";
  public static final String PASTED = "PASTED";
}
