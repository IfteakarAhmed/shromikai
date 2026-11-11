package bd.shromikai.ingest;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Synthetic PDFs and images built in memory for intake tests (no real contracts, R-02). */
final class PdfFixtures {

  private PdfFixtures() {}

  /** A PDF with one page per entry; an empty list makes a page with no text layer. */
  static byte[] pdf(List<List<String>> pages) {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (List<String> lines : pages) {
        PDPage page = new PDPage();
        doc.addPage(page);
        if (lines.isEmpty()) {
          continue;
        }
        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
          content.setFont(font, 11);
          content.beginText();
          content.setLeading(14);
          content.newLineAtOffset(50, 740);
          for (String line : lines) {
            content.showText(line);
            content.newLine();
          }
          content.endText();
        }
      }
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] png(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    return encode(image, "png");
  }

  /** A PNG with the text drawn on it (Latin only, system font). */
  static byte[] pngWithText(String text) {
    BufferedImage image = new BufferedImage(1000, 200, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 1000, 200);
    g.setColor(Color.BLACK);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
    g.drawString(text, 30, 110);
    g.dispose();
    return encode(image, "png");
  }

  static byte[] encode(BufferedImage image, String format) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
