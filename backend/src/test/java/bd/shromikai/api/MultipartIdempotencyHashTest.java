package bd.shromikai.api;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.analysis.Side;
import bd.shromikai.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-58: the idempotency key of a file upload depends on the file bytes, not only their size. */
@Tag("unit")
@Requirement("FR-58")
class MultipartIdempotencyHashTest {

  private static final AnalysesController.Options OPTIONS =
      new AnalysesController.Options(Side.WORKER, "en", null, null, null, null);

  @Test
  void twoFilesOfTheSameLengthButDifferentBytesHashDifferently() {
    String first = AnalysesController.multipartHash(null, List.of("AAAA".getBytes()), OPTIONS);
    String second = AnalysesController.multipartHash(null, List.of("BBBB".getBytes()), OPTIONS);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void theSameBytesAndOptionsHashTheSame() {
    assertThat(AnalysesController.multipartHash(null, List.of("AAAA".getBytes()), OPTIONS))
        .isEqualTo(AnalysesController.multipartHash(null, List.of("AAAA".getBytes()), OPTIONS));
  }

  @Test
  void theOrderOfImagesCountsAndAFileIsNotConfusedWithText() {
    byte[] a = "AAAA".getBytes();
    byte[] b = "BBBB".getBytes();

    assertThat(AnalysesController.multipartHash(null, List.of(a, b), OPTIONS))
        .isNotEqualTo(AnalysesController.multipartHash(null, List.of(b, a), OPTIONS));
    assertThat(AnalysesController.multipartHash("AAAA", List.of(), OPTIONS))
        .isNotEqualTo(AnalysesController.multipartHash(null, List.of(a), OPTIONS));
  }
}
