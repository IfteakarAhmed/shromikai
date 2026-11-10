package bd.shromikai.shared.ai;

import bd.shromikai.shared.SensitiveText;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One rendered prompt for one model call. The user turn may hold redacted contract text, so it is a
 * {@link SensitiveText}; the system turn is trusted text from a prompt file.
 *
 * @param temperature {@code null} means provider default (required for Claude, ADR-0005)
 * @param effort Claude effort; {@code null} for other providers
 */
public record ChatRequest(
    String promptId,
    int promptVersion,
    ModelRole role,
    String modelId,
    String system,
    SensitiveText user,
    JsonNode outputSchema,
    int maxOutputTokens,
    Duration timeout,
    @Nullable Double temperature,
    @Nullable Effort effort) {

  public ChatRequest {
    Objects.requireNonNull(promptId, "promptId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(modelId, "modelId");
    Objects.requireNonNull(system, "system");
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(outputSchema, "outputSchema");
    Objects.requireNonNull(timeout, "timeout");
    if (promptVersion < 1) {
      throw new IllegalArgumentException("promptVersion must be positive");
    }
    if (maxOutputTokens < 1) {
      throw new IllegalArgumentException("maxOutputTokens must be positive");
    }
  }

  /**
   * SHA-256 (lower-case hex) over the rendered request: prompt ID, version, system and user turn,
   * each terminated by U+0000. Recorded fixtures store it to detect prompt or input drift.
   */
  public String renderedSha256() {
    String canonical =
        promptId + '\0' + promptVersion + '\0' + system + '\0' + user.reveal() + '\0';
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
