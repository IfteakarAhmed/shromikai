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

  public RecordingChatPort(ChatPort delegate, Path root, String caseId) {
    this.delegate = delegate;
    this.root = root;
    this.caseId = caseId;
  }

  @Override
  public String provider() {
    return delegate.provider();
  }

  @Override
  public ChatResponse call(ChatRequest request) {
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
