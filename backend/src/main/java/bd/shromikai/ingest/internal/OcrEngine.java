package bd.shromikai.ingest.internal;

import java.awt.image.BufferedImage;

/** Local OCR of one page image. Implementations never send the image off the machine (R-03). */
public interface OcrEngine {

  /**
   * @param confidence mean word confidence 0 to 100, 0 if no word was recognised
   */
  record Page(String text, double confidence) {}

  Page read(BufferedImage image);
}
