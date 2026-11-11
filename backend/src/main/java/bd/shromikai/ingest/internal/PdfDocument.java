package bd.shromikai.ingest.internal;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * An uploaded PDF held entirely in memory: PDFBox uses a memory-only stream cache, so it never
 * writes a scratch file (R-02, architecture §10.1).
 */
public final class PdfDocument implements AutoCloseable {

  private final PDDocument document;
  private final long maxPixels;

  private PdfDocument(PDDocument document, long maxPixels) {
    this.document = document;
    this.maxPixels = maxPixels;
  }

  public static PdfDocument open(byte[] bytes, long maxPixels) {
    try {
      PDDocument doc =
          Loader.loadPDF(
              new RandomAccessReadBuffer(bytes),
              "",
              null,
              null,
              IOUtils.createMemoryOnlyStreamCache());
      if (doc.isEncrypted()) {
        doc.close();
        throw new ProblemException(
            ProblemType.UNSUPPORTED_MEDIA_TYPE, "Encrypted PDF files are not accepted.");
      }
      return new PdfDocument(doc, maxPixels);
    } catch (IOException e) {
      throw new ProblemException(
          ProblemType.UNSUPPORTED_MEDIA_TYPE, "The PDF file is damaged or cannot be read.");
    }
  }

  public int pageCount() {
    return document.getNumberOfPages();
  }

  /** Text layer of one page (zero-based index), one entry per line. */
  public List<String> lines(int index) {
    try {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(index + 1);
      stripper.setEndPage(index + 1);
      stripper.setSortByPosition(true);
      List<String> lines = new ArrayList<>();
      for (String line : stripper.getText(document).split("\\R", -1)) {
        lines.add(line);
      }
      return lines;
    } catch (IOException e) {
      throw new ProblemException(
          ProblemType.UNSUPPORTED_MEDIA_TYPE, "The PDF text could not be read.");
    }
  }

  /**
   * Renders one page in memory at the given DPI, lowered if the pixel count would exceed the cap.
   */
  public BufferedImage render(int index, int dpi) {
    try {
      PDPage page = document.getPage(index);
      float widthPt = page.getMediaBox().getWidth();
      float heightPt = page.getMediaBox().getHeight();
      double scale = dpi / 72.0;
      double pixels = widthPt * scale * heightPt * scale;
      int effective =
          pixels <= maxPixels ? dpi : (int) Math.max(72, dpi * Math.sqrt(maxPixels / pixels));
      return new PDFRenderer(document).renderImageWithDPI(index, effective, ImageType.GRAY);
    } catch (IOException e) {
      throw new ProblemException(
          ProblemType.UNSUPPORTED_MEDIA_TYPE, "A PDF page could not be rendered.");
    }
  }

  @Override
  public void close() {
    try {
      document.close();
    } catch (IOException ignored) {
      // Nothing is on disk; closing only frees memory.
    }
  }
}
