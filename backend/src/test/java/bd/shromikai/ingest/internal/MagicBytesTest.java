package bd.shromikai.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-24: the type comes from the magic bytes, never from a file name. */
@Tag("unit")
@Requirement("FR-24")
class MagicBytesTest {

  private static byte[] bytes(int... values) {
    byte[] out = new byte[values.length + 16];
    for (int i = 0; i < values.length; i++) {
      out[i] = (byte) values[i];
    }
    return out;
  }

  @Test
  void shouldRecogniseAPdfByItsSignatureWhateverItIsCalled() {
    assertThat(MagicBytes.detect("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo(FileType.PDF);
  }

  @Test
  void shouldRecogniseJpegAndPng() {
    assertThat(MagicBytes.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(FileType.JPEG);
    assertThat(MagicBytes.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)))
        .isEqualTo(FileType.PNG);
  }

  @Test
  void shouldAcceptPlainUtf8TextInBanglaAndEnglish() {
    assertThat(
            MagicBytes.detect(
                "চাকরির চুক্তি\nEmployment contract".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo(FileType.TEXT);
  }

  @Test
  void shouldRejectARenamedWindowsExecutableWithAProblem() {
    byte[] exe = bytes('M', 'Z', 0x90, 0x00, 0x03, 0x00);

    assertThatThrownBy(() -> MagicBytes.detect(exe))
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  void shouldRejectElfMachOZipScriptsAndEmptyOrBinaryData() {
    for (byte[] bad :
        new byte[][] {
          bytes(0x7F, 'E', 'L', 'F'),
          bytes(0xCA, 0xFE, 0xBA, 0xBE),
          bytes('P', 'K', 0x03, 0x04),
          "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8),
          new byte[0],
          bytes(0x00, 0x01, 0x02, 0x03),
          bytes(0xC3, 0x28)
        }) {
      assertThatThrownBy(() -> MagicBytes.detect(bad)).isInstanceOf(ProblemException.class);
    }
  }
}
