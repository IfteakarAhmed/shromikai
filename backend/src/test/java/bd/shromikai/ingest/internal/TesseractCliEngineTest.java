package bd.shromikai.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.testsupport.Requirement;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** NFR-20: a hung, endless or failing OCR child process cannot block or exhaust the service. */
@Tag("unit")
@Requirement("FR-27")
class TesseractCliEngineTest {

  private static final BufferedImage IMAGE = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);

  @Test
  @Timeout(15)
  void aChildThatSleepsIsKilledAtTheTimeout() {
    var engine = new TesseractCliEngine(List.of("sh", "-c", "sleep 60"), Duration.ofMillis(800));
    long start = System.nanoTime();
    assertThatThrownBy(() -> engine.read(IMAGE))
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("timed out");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  @Timeout(15)
  void aChildThatWritesWithoutEndIsStoppedAtTheOutputCap() {
    var engine =
        new TesseractCliEngine(List.of("sh", "-c", "yes"), Duration.ofSeconds(10), 64 * 1024);
    assertThatThrownBy(() -> engine.read(IMAGE))
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("too large");
  }

  @Test
  @Timeout(15)
  void aFailingChildIsReported() {
    var engine = new TesseractCliEngine(List.of("sh", "-c", "exit 3"), Duration.ofSeconds(5));
    assertThatThrownBy(() -> engine.read(IMAGE))
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("failed");
  }
}
