package bd.shromikai.ingest.internal;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Tesseract 5 as a child process: the page image goes in as PNG on stdin and the word table ({@code
 * tsv}) comes back on stdout, so no file is created (R-02, architecture §10.1). The command is
 * configurable ({@code shromikai.intake.tesseract-command}); a Docker command works too.
 */
public final class TesseractCliEngine implements OcrEngine {

  private final List<String> command;
  private final Duration timeout;

  public TesseractCliEngine(List<String> command, Duration timeout) {
    this.command = List.copyOf(command);
    this.timeout = timeout;
  }

  @Override
  public Page read(BufferedImage image) {
    byte[] png = encode(image);
    Process process;
    try {
      process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    } catch (IOException e) {
      throw new ProblemException(
          ProblemType.INTERNAL_ERROR, "Local OCR engine could not be started.");
    }
    try {
      Thread writer =
          Thread.startVirtualThread(
              () -> {
                try (OutputStream stdin = process.getOutputStream()) {
                  stdin.write(png);
                } catch (IOException ignored) {
                  // The process ended early; the exit code below reports it.
                }
              });
      byte[] output;
      try (InputStream stdout = process.getInputStream()) {
        output = readAll(stdout);
      }
      writer.join();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new ProblemException(ProblemType.INTERNAL_ERROR, "Local OCR timed out.");
      }
      if (process.exitValue() != 0) {
        throw new ProblemException(ProblemType.INTERNAL_ERROR, "Local OCR failed.");
      }
      return parse(new String(output, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Local OCR failed.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Local OCR was interrupted.");
    }
  }

  private static byte[] encode(BufferedImage image) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Image could not be encoded.");
    }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    return in.readAllBytes();
  }

  /** Parses Tesseract's TSV: words (level 5) grouped by block, paragraph and line. */
  static Page parse(String tsv) {
    StringBuilder text = new StringBuilder();
    double sum = 0;
    int words = 0;
    String lastLine = null;
    String lastPar = null;
    for (String row : tsv.split("\n")) {
      String[] c = row.split("\t", -1);
      if (c.length < 12 || !"5".equals(c[0])) {
        continue;
      }
      String word = c[11].strip();
      double conf;
      try {
        conf = Double.parseDouble(c[10]);
      } catch (NumberFormatException e) {
        continue;
      }
      if (word.isEmpty() || conf < 0) {
        continue;
      }
      String par = c[2] + "." + c[3];
      String line = par + "." + c[4];
      if (lastLine != null && !line.equals(lastLine)) {
        text.append(par.equals(lastPar) ? "\n" : "\n\n");
      } else if (lastLine != null) {
        text.append(' ');
      }
      text.append(word);
      lastLine = line;
      lastPar = par;
      sum += conf;
      words++;
    }
    return new Page(text.toString(), words == 0 ? 0 : sum / words);
  }
}
