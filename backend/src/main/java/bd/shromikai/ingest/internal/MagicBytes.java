package bd.shromikai.ingest.internal;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Recognises an upload by its magic bytes, never by file name or declared content type (FR-24).
 * Anything else, including executables renamed to {@code .pdf}, is {@code unsupported-media-type}.
 */
public final class MagicBytes {

  private MagicBytes() {}

  public static FileType detect(byte[] bytes) {
    if (startsWith(bytes, '%', 'P', 'D', 'F', '-')) {
      return FileType.PDF;
    }
    if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
      return FileType.JPEG;
    }
    if (startsWith(bytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return FileType.PNG;
    }
    if (isPlainText(bytes)) {
      return FileType.TEXT;
    }
    throw new ProblemException(
        ProblemType.UNSUPPORTED_MEDIA_TYPE, "Only PDF, JPEG, PNG or plain text are accepted.");
  }

  private static boolean startsWith(byte[] bytes, int... signature) {
    if (bytes.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((bytes[i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }

  /** Valid UTF-8 with no control characters other than whitespace, and no binary signature. */
  private static boolean isPlainText(byte[] bytes) {
    if (bytes.length == 0
        || startsWith(bytes, 'M', 'Z')
        || startsWith(bytes, 0x7F, 'E', 'L', 'F')
        || startsWith(bytes, '#', '!')
        || startsWith(bytes, 0xCA, 0xFE, 0xBA, 0xBE)
        || startsWith(bytes, 'P', 'K', 0x03, 0x04)) {
      return false;
    }
    CharBuffer chars;
    try {
      chars =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes));
    } catch (CharacterCodingException e) {
      return false;
    }
    for (int i = 0; i < chars.length(); i++) {
      char c = chars.charAt(i);
      if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
        return false;
      }
    }
    return true;
  }
}
