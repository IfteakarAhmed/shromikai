package bd.shromikai.ingest;

import bd.shromikai.ingest.internal.BijoyConverter;
import bd.shromikai.ingest.internal.FileType;
import bd.shromikai.ingest.internal.HeaderFooterRemover;
import bd.shromikai.ingest.internal.HyphenationRepair;
import bd.shromikai.ingest.internal.ImageDecoder;
import bd.shromikai.ingest.internal.MagicBytes;
import bd.shromikai.ingest.internal.OcrEngine;
import bd.shromikai.ingest.internal.Paragrapher;
import bd.shromikai.ingest.internal.PdfDocument;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Contract intake (FR-24 to FR-27): validates the upload by magic bytes, reads PDF text layers with
 * PDFBox, converts legacy Bangla fonts, sends scanned pages and images to local OCR, removes
 * repeated headers and footers, repairs hyphenation and returns numbered paragraphs. Everything
 * stays in memory (R-02); nothing here talks to the network (R-03).
 */
@Service
public final class IntakeService {

  /** A page with fewer non-whitespace characters in its text layer than this is scanned. */
  static final int MIN_TEXT_LAYER_CHARS = 50;

  /** OCR pages below this mean confidence are flagged {@code LOW_OCR_CONFIDENCE}. */
  static final double LOW_CONFIDENCE = 80.0;

  static final int MIN_TEXT = 200;
  static final int MAX_TEXT = 60_000;

  static {
    ImageIO.setUseCache(false); // no ImageIO disk cache (R-02)
  }

  private final IntakeProperties props;
  private final OcrEngine ocr;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public IntakeService(IntakeProperties props, OcrEngine ocr) {
    this.props = props;
    this.ocr = ocr;
  }

  /** Pasted text of 200 to 60,000 characters. */
  public IntakeResult fromText(String text) {
    String clean = Normalizer.normalize(text, Normalizer.Form.NFC);
    int length = clean.codePointCount(0, clean.length());
    if (length < MIN_TEXT || length > MAX_TEXT) {
      throw new ProblemException(
          ProblemType.VALIDATION_FAILED,
          "Text must be between " + MIN_TEXT + " and " + MAX_TEXT + " characters.");
    }
    List<String> lines = Arrays.asList(clean.split("\\R", -1));
    List<Paragrapher.Line> all = new ArrayList<>();
    lines.forEach(l -> all.add(new Paragrapher.Line(1, l)));
    List<Paragrapher.Para> paras = Paragrapher.split(all);
    return result(
        InputKind.TEXT,
        1,
        paras,
        List.of(new PageQuality(1, PageQuality.PASTED, null)),
        false,
        new TreeMap<>());
  }

  /** One PDF, or 1 to 15 JPEG/PNG images, or one plain-text file. */
  public IntakeResult fromFiles(List<byte[]> files) {
    if (files.isEmpty()) {
      throw new ProblemException(ProblemType.VALIDATION_FAILED, "No file was sent.");
    }
    long total = files.stream().mapToLong(f -> f.length).sum();
    if (total > props.maxBytes()) {
      throw new ProblemException(
          ProblemType.PAYLOAD_TOO_LARGE, "Uploads may be at most 10 MB in total.");
    }
    List<FileType> types = files.stream().map(MagicBytes::detect).toList();
    FileType first = types.getFirst();
    if (first == FileType.PDF || first == FileType.TEXT) {
      if (files.size() != 1) {
        throw new ProblemException(
            ProblemType.UNSUPPORTED_MEDIA_TYPE, "Send one PDF, one text file, or images only.");
      }
      return first == FileType.PDF
          ? fromPdf(files.getFirst())
          : fromText(new String(files.getFirst(), StandardCharsets.UTF_8));
    }
    if (types.stream().anyMatch(t -> t != FileType.JPEG && t != FileType.PNG)) {
      throw new ProblemException(
          ProblemType.UNSUPPORTED_MEDIA_TYPE, "Do not mix images with other file types.");
    }
    if (files.size() > props.maxPages()) {
      throw new ProblemException(ProblemType.TOO_MANY_PAGES, "At most 15 images are accepted.");
    }
    return fromImages(files);
  }

  private IntakeResult fromImages(List<byte[]> files) {
    List<BufferedImage> images = new ArrayList<>();
    for (byte[] file : files) {
      images.add(ImageDecoder.decode(file, props.maxImageSide(), props.maxPixels()));
    }
    List<OcrEngine.Page> read = ocrAll(images);
    TreeMap<Integer, Double> confidence = new TreeMap<>();
    List<Paragrapher.Line> lines = new ArrayList<>();
    List<List<String>> pages = new ArrayList<>();
    for (int i = 0; i < read.size(); i++) {
      confidence.put(i + 1, read.get(i).confidence());
      pages.add(Arrays.asList(read.get(i).text().split("\\R", -1)));
    }
    requireReadable(confidence);
    collect(pages, lines, new TreeMap<>());
    List<PageQuality> quality = new ArrayList<>();
    confidence.forEach((p, c) -> quality.add(new PageQuality(p, PageQuality.OCR, c)));
    return result(
        InputKind.IMAGE, files.size(), Paragrapher.split(lines), quality, false, confidence);
  }

  private IntakeResult fromPdf(byte[] bytes) {
    Future<IntakeResult> future = executor.submit(() -> readPdf(bytes));
    try {
      return future.get(
          props.pdfTimeout().toMillis() + props.ocrTimeout().toMillis() * 4, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new ProblemException(
          ProblemType.UNSUPPORTED_MEDIA_TYPE, "The PDF took too long to read.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Reading was interrupted.");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ProblemException problem) {
        throw problem;
      }
      throw new ProblemException(ProblemType.UNSUPPORTED_MEDIA_TYPE, "The PDF could not be read.");
    }
  }

  private IntakeResult readPdf(byte[] bytes) {
    try (PdfDocument pdf = PdfDocument.open(bytes, props.maxPixels())) {
      int count = pdf.pageCount();
      if (count > props.maxPages()) {
        throw new ProblemException(ProblemType.TOO_MANY_PAGES, "At most 15 pages are accepted.");
      }
      if (count == 0) {
        throw new ProblemException(ProblemType.UNSUPPORTED_MEDIA_TYPE, "The PDF has no pages.");
      }
      List<List<String>> pageLines = new ArrayList<>();
      List<Integer> needOcr = new ArrayList<>();
      List<Boolean> legacy = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        List<String> lines = pdf.lines(i);
        String joined = String.join("\n", lines);
        boolean converted = false;
        if (nonWhitespace(joined) >= MIN_TEXT_LAYER_CHARS && BijoyConverter.looksLegacy(joined)) {
          String unicode =
              Normalizer.normalize(BijoyConverter.toUnicode(joined), Normalizer.Form.NFC);
          if (BijoyConverter.bengaliShare(unicode) >= BijoyConverter.MIN_BENGALI_SHARE) {
            lines = Arrays.asList(unicode.split("\\R", -1));
            joined = unicode;
            converted = true;
          } else {
            joined = "";
          }
        }
        legacy.add(converted);
        if (nonWhitespace(joined) < MIN_TEXT_LAYER_CHARS) {
          needOcr.add(i);
          pageLines.add(List.of());
        } else {
          pageLines.add(
              lines.stream().map(l -> Normalizer.normalize(l, Normalizer.Form.NFC)).toList());
        }
      }
      TreeMap<Integer, Double> confidence = new TreeMap<>();
      if (!needOcr.isEmpty()) {
        List<BufferedImage> images = new ArrayList<>();
        for (int index : needOcr) {
          images.add(pdf.render(index, props.rasterDpi()));
        }
        List<OcrEngine.Page> read = ocrAll(images);
        for (int k = 0; k < needOcr.size(); k++) {
          int index = needOcr.get(k);
          confidence.put(index + 1, read.get(k).confidence());
          pageLines.set(
              index,
              Arrays.stream(
                      Normalizer.normalize(read.get(k).text(), Normalizer.Form.NFC)
                          .split("\\R", -1))
                  .toList());
        }
        requireReadable(confidence);
      }
      List<Paragrapher.Line> lines = new ArrayList<>();
      TreeMap<Integer, Boolean> legacyPages = new TreeMap<>();
      for (int i = 0; i < legacy.size(); i++) {
        if (legacy.get(i)) {
          legacyPages.put(i + 1, true);
        }
      }
      collect(pageLines, lines, legacyPages);
      List<PageQuality> quality = new ArrayList<>();
      for (int p = 1; p <= count; p++) {
        quality.add(
            confidence.containsKey(p)
                ? new PageQuality(p, PageQuality.OCR, confidence.get(p))
                : new PageQuality(
                    p,
                    legacyPages.containsKey(p) ? PageQuality.LEGACY_FONT : PageQuality.TEXT_LAYER,
                    null));
      }
      return result(
          confidence.isEmpty() ? InputKind.PDF_TEXT : InputKind.PDF_SCAN,
          count,
          Paragrapher.split(lines),
          quality,
          !legacyPages.isEmpty(),
          confidence,
          legacyPages);
    }
  }

  /** Removes headers/footers, repairs hyphenation and flattens pages into numbered lines. */
  private static void collect(
      List<List<String>> pages, List<Paragrapher.Line> out, TreeMap<Integer, Boolean> unused) {
    List<List<String>> trimmed = HeaderFooterRemover.remove(pages);
    for (int p = 0; p < trimmed.size(); p++) {
      for (String line : HyphenationRepair.repair(trimmed.get(p))) {
        out.add(new Paragrapher.Line(p + 1, line));
      }
    }
  }

  private List<OcrEngine.Page> ocrAll(List<BufferedImage> images) {
    Semaphore permits = new Semaphore(props.ocrParallel());
    List<Future<OcrEngine.Page>> futures = new ArrayList<>();
    for (BufferedImage image : images) {
      futures.add(
          executor.submit(
              () -> {
                permits.acquire();
                try {
                  return ocr.read(image);
                } finally {
                  permits.release();
                }
              }));
    }
    List<OcrEngine.Page> out = new ArrayList<>();
    try {
      for (Future<OcrEngine.Page> future : futures) {
        out.add(future.get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      futures.forEach(f -> f.cancel(true));
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Reading was interrupted.");
    } catch (ExecutionException e) {
      futures.forEach(f -> f.cancel(true));
      if (e.getCause() instanceof ProblemException problem) {
        throw problem;
      }
      throw new ProblemException(ProblemType.INTERNAL_ERROR, "Local OCR failed.");
    }
    return out;
  }

  private void requireReadable(TreeMap<Integer, Double> confidence) {
    List<Integer> bad = new ArrayList<>();
    confidence.forEach(
        (page, conf) -> {
          if (conf < props.minOcrConfidence()) {
            bad.add(page);
          }
        });
    if (!bad.isEmpty()) {
      throw new UnreadableDocumentException(bad);
    }
  }

  private IntakeResult result(
      InputKind kind,
      int pageCount,
      List<Paragrapher.Para> paras,
      List<PageQuality> quality,
      boolean anyLegacy,
      TreeMap<Integer, Double> confidence) {
    return result(kind, pageCount, paras, quality, anyLegacy, confidence, new TreeMap<>());
  }

  private IntakeResult result(
      InputKind kind,
      int pageCount,
      List<Paragrapher.Para> paras,
      List<PageQuality> quality,
      boolean anyLegacy,
      TreeMap<Integer, Double> confidence,
      TreeMap<Integer, Boolean> legacyPages) {
    List<IntakeParagraph> out = new ArrayList<>();
    StringBuilder all = new StringBuilder();
    for (int i = 0; i < paras.size(); i++) {
      Paragrapher.Para para = paras.get(i);
      Double conf = confidence.get(para.page());
      out.add(
          new IntakeParagraph(
              "p" + (i + 1),
              para.page(),
              SensitiveText.of(para.text()),
              conf != null && conf < LOW_CONFIDENCE,
              legacyPages.containsKey(para.page())));
      all.append(para.text()).append(' ');
    }
    if (out.isEmpty()) {
      throw new ProblemException(
          ProblemType.UNSUPPORTED_MEDIA_TYPE, "No readable text was found in the document.");
    }
    return new IntakeResult(kind, pageCount, Paragrapher.language(all.toString()), out, quality);
  }

  private static int nonWhitespace(String text) {
    int n = 0;
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        n++;
      }
    }
    return n;
  }
}
