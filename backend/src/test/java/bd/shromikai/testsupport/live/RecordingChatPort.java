package bd.shromikai.testsupport.live;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Records every successful live call as a fixture for {@code FakeChatPort} (test-strategy.md §6.1):
 * {@code {root}/{promptId}/v{n}/{caseId}/{requestSha[:16]}.json}, addressed by the hash of the
 * rendered request. Only synthetic contracts are ever recorded (R-02, test-strategy §13); the file
 * says it was recorded from a live call.
 */
public final class RecordingChatPort implements ChatPort {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final ChatPort delegate;
  private final Path root;
  private final String caseId;
  private final boolean replayExisting;

  public RecordingChatPort(ChatPort delegate, Path root, String caseId) {
    this(delegate, root, caseId, false);
  }

  /**
   * @param replayExisting answer from a recording of the same request (same hash) instead of
   *     calling again, so a repeated run costs nothing for calls that already succeeded
   */
  public RecordingChatPort(ChatPort delegate, Path root, String caseId, boolean replayExisting) {
    this.delegate = delegate;
    this.root = root;
    this.caseId = caseId;
    this.replayExisting = replayExisting;
  }

  @Override
  public String provider() {
    return delegate.provider();
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    Path existing =
        root.resolve(request.promptId())
            .resolve("v" + request.promptVersion())
            .resolve(caseId)
            .resolve(request.renderedSha256().substring(0, 16) + ".json");
    if (replayExisting && Files.isRegularFile(existing)) {
      try {
        var fixture = MAPPER.readTree(existing.toFile());
        var usage = fixture.path("usage");
        return new ChatResponse(
            fixture.get("response"),
            fixture.path("modelId").asText(),
            new bd.shromikai.shared.ai.TokenUsage(
                usage.path("inputTokens").asLong(),
                usage.path("outputTokens").asLong(),
                usage.path("cacheReadTokens").asLong(),
                0),
            true,
            fixture.hasNonNull("stopReason") ? fixture.get("stopReason").asText() : null);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    ChatResponse response = delegate.call(request);
    ObjectNode fixture = MAPPER.createObjectNode();
    fixture.put("fixtureVersion", 1);
    fixture.put("promptId", request.promptId());
    fixture.put("promptVersion", request.promptVersion());
    fixture.put("caseId", caseId);
    fixture.put("modelId", response.modelId());
    fixture.put("requestSha256", request.renderedSha256());
    fixture.put("provenance", "synthetic");
    fixture.put("source", "recorded from a live call");
    fixture.put("recordedAt", Instant.now().toString());
    if (response.stopReason() != null) {
      fixture.put("stopReason", response.stopReason());
    }
    fixture
        .putObject("usage")
        .put("inputTokens", response.usage().inputTokens())
        .put("outputTokens", response.usage().outputTokens())
        .put("cacheReadTokens", response.usage().cacheReadTokens());
    fixture.set("response", response.output());
    Path file =
        root.resolve(request.promptId())
            .resolve("v" + request.promptVersion())
            .resolve(caseId)
            .resolve(request.renderedSha256().substring(0, 16) + ".json");
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, MAPPER.writeValueAsString(fixture) + "\n", StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return response;
  }
}
