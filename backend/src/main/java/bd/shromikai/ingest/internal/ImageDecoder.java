package bd.shromikai.ingest.internal;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Decodes JPEG and PNG uploads in memory. The size is read from the header and checked against the
 * side and pixel limits <em>before</em> any pixel is decoded, so a decompression bomb is refused
 * cheaply (NFR-20).
 */
public final class ImageDecoder {

  private ImageDecoder() {}

  public static BufferedImage decode(byte[] bytes, int maxSide, long maxPixels) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw unreadable();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width > maxSide || height > maxSide || (long) width * height > maxPixels) {
          throw new ProblemException(
              ProblemType.PAYLOAD_TOO_LARGE,
              "Images may be at most " + maxSide + " by " + maxSide + " pixels.");
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          throw unreadable();
        }
        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ProblemException problem) {
        throw problem;
      }
      throw unreadable();
    }
  }

  private static ProblemException unreadable() {
    return new ProblemException(
        ProblemType.UNSUPPORTED_MEDIA_TYPE, "The image file is damaged or cannot be read.");
  }
}
