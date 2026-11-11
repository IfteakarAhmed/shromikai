package bd.shromikai.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.ingest.internal.OcrEngine;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** NFR-20: oversized, over-long, bomb-like and flooding uploads are refused cheaply. */
@Tag("it")
@Requirement("NFR-20")
@Requirement("FR-24")
class AbuseLimitsIT {

  private static IntakeProperties props(int maxConcurrent, Duration ocrTimeout) {
    IntakeProperties d = IntakeProperties.defaults();
    return new IntakeProperties(
        d.maxBytes(),
        d.maxPages(),
        d.maxPixels(),
        d.maxImageSide(),
        d.pdfTimeout(),
        ocrTimeout,
        d.minOcrConfidence(),
        d.ocrParallel(),
        d.rasterDpi(),
        maxConcurrent,
        d.tesseractCommand());
  }

  private static final OcrEngine NEVER_CALLED =
      image -> {
        throw new AssertionError("OCR must not run");
      };

  private static ProblemType type(Throwable t) {
    return ((ProblemException) t).type();
  }

  @Test
  void shouldRefuseAnUploadOverTheByteLimitBeforeReadingIt() {
    var service = new IntakeService(props(4, Duration.ofSeconds(5)), NEVER_CALLED);
    byte[] big = new byte[10 * 1024 * 1024 + 1];

    assertThatThrownBy(() -> service.fromFiles(List.of(big)))
        .isInstanceOf(ProblemException.class)
        .satisfies(t -> assertThat(type(t)).isEqualTo(ProblemType.PAYLOAD_TOO_LARGE));
  }

  @Test
  void shouldRefuseASixteenPagePdfWithoutOcr() {
    List<List<String>> pages = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      pages.add(List.of());
    }
    var service = new IntakeService(props(4, Duration.ofSeconds(5)), NEVER_CALLED);

    assertThatThrownBy(() -> service.fromFiles(List.of(PdfFixtures.pdf(pages))))
        .isInstanceOf(ProblemException.class)
        .satisfies(t -> assertThat(type(t)).isEqualTo(ProblemType.TOO_MANY_PAGES));
  }

  @Test
  void shouldRefuseADecompressionBombPngFromItsHeaderAlone() {
    // 20,000 x 20,000 grey pixels (400 MB decoded) compress to a few hundred kilobytes.
    byte[] bomb = PdfFixtures.png(20_000, 20_000);
    assertThat(bomb.length).isLessThan(10 * 1024 * 1024);
    var service = new IntakeService(props(4, Duration.ofSeconds(5)), NEVER_CALLED);

    assertThatThrownBy(() -> service.fromFiles(List.of(bomb)))
        .isInstanceOf(ProblemException.class)
        .satisfies(t -> assertThat(type(t)).isEqualTo(ProblemType.PAYLOAD_TOO_LARGE));
  }

  @Test
  @Timeout(30)
  void shouldDecodeAtMostOcrParallelImagesAtOnce() {
    AtomicInteger live = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    OcrEngine slow =
        image -> {
          int now = live.incrementAndGet();
          peak.accumulateAndGet(now, Math::max);
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          live.decrementAndGet();
          return new OcrEngine.Page("1. Clause text that is readable enough here.", 95);
        };
    var service = new IntakeService(props(4, Duration.ofSeconds(10)), slow);
    List<byte[]> images = Collections.nCopies(15, PdfFixtures.png(200, 200));

    service.fromFiles(images);

    assertThat(peak.get()).isLessThanOrEqualTo(IntakeProperties.defaults().ocrParallel());
  }

  @Test
  @Timeout(30)
  void shouldTimeOutAnImageUploadWhoseOcrHangs() {
    OcrEngine hung =
        image -> {
          try {
            Thread.sleep(60_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new OcrEngine.Page("", 0);
        };
    var service = new IntakeService(props(4, Duration.ofMillis(300)), hung);

    assertThatThrownBy(() -> service.fromFiles(List.of(PdfFixtures.png(100, 100))))
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("too long");
  }

  @Test
  @Timeout(30)
  void shouldRefuseUploadsBeyondTheGlobalConcurrencyLimit() throws Exception {
    CountDownLatch inside = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    OcrEngine blocking =
        image -> {
          inside.countDown();
          try {
            release.await(20, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new OcrEngine.Page("1. Clause text that is readable enough here.", 95);
        };
    var service = new IntakeService(props(2, Duration.ofSeconds(20)), blocking);
    byte[] png = PdfFixtures.png(100, 100);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<?> a = pool.submit(() -> service.fromFiles(List.of(png)));
      Future<?> b = pool.submit(() -> service.fromFiles(List.of(png)));
      assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> service.fromFiles(List.of(png)))
          .isInstanceOf(ProblemException.class)
          .satisfies(t -> assertThat(type(t)).isEqualTo(ProblemType.CAPACITY_EXHAUSTED));

      release.countDown();
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
    }
    assertThat(service.fromFiles(List.of(png))).isNotNull(); // slots were released
  }
}
