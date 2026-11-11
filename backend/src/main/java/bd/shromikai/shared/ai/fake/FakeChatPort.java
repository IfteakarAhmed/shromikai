package bd.shromikai.shared.ai.fake;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.json.JsonSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Deterministic {@link ChatPort} that replays recorded responses by prompt ID, version and case ID
 * (test-strategy.md §6.1). Fixtures live at {@code {root}/{promptId}/v{version}/{caseId}.json} on
 * the classpath. Every fixture response is validated against the prompt's output schema when
 * loaded, and its {@code requestSha256} must match the rendered request.
 */
public final class FakeChatPort implements ChatPort {

  static final Set<String> PROVENANCES = Set.of("synthetic", "public-template");
  static final int FIXTURE_VERSION = 1;

  private final String root;
  private final ObjectMapper mapper;
  private final JsonSchemas schemas;
  private final ClassLoader classLoader;
  private final String defaultCase;

  private volatile String currentCase;
  private final Map<String, Function<ChatRequest, JsonNode>> inline = new ConcurrentHashMap<>();
  private final Map<String, FailureMode> failures = new ConcurrentHashMap<>();
  private final Set<String> invalidOnceServed = ConcurrentHashMap.newKeySet();
  private final List<RecordedCall> calls = new CopyOnWriteArrayList<>();

  public FakeChatPort(String root, ObjectMapper mapper, JsonSchemas schemas, String defaultCase) {
    this(root, mapper, schemas, defaultCase, FakeChatPort.class.getClassLoader());
  }

  /** Fixtures from another class loader, for tests that record into a temporary directory. */
  public FakeChatPort(
      String root,
      ObjectMapper mapper,
      JsonSchemas schemas,
      String defaultCase,
      ClassLoader classLoader) {
    this.root = stripSlashes(Objects.requireNonNull(root, "root"));
    this.mapper = mapper;
    this.schemas = schemas;
    this.defaultCase = defaultCase;
    this.currentCase = defaultCase;
    this.classLoader = classLoader;
  }

  @Override
  public String provider() {
    return "fake";
  }

  /** Selects the fixture case for subsequent calls. */
  public void useCase(String caseId) {
    this.currentCase = Objects.requireNonNull(caseId);
  }

  /** Answers a prompt with a fixed response or a function of the request instead of a fixture. */
  public void respondWith(String promptId, Function<ChatRequest, JsonNode> response) {
    inline.put(promptId, response);
  }

  public void failWith(String promptId, FailureMode mode) {
    failures.put(promptId, mode);
  }

  public List<RecordedCall> calls() {
    return List.copyOf(calls);
  }

  /** Clears inline responses, failure modes, recorded calls and the selected case. */
  public void reset() {
    inline.clear();
    failures.clear();
    invalidOnceServed.clear();
    calls.clear();
    currentCase = defaultCase;
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    String caseId = currentCase;
    calls.add(new RecordedCall(request.promptId(), request.promptVersion(), caseId, request));
    FailureMode failure = failures.get(request.promptId());
    if (failure != null) {
      ChatResponse failed = applyFailure(failure, request);
      if (failed != null) {
        return failed;
      }
    }
    Function<ChatRequest, JsonNode> response = inline.get(request.promptId());
    if (response != null) {
      return new ChatResponse(response.apply(request), request.modelId(), TokenUsage.NONE, true);
    }
    return replay(request, caseId);
  }

  private ChatResponse applyFailure(FailureMode mode, ChatRequest request) {
    return switch (mode) {
      case TIMEOUT -> throw new ModelTimeoutException("fake-timeout");
      case RATE_LIMITED -> throw new UpstreamUnavailableException("fake-rate-limited", 429, 1);
      case SERVER_ERROR -> throw new UpstreamUnavailableException("fake-server-error", 500, null);
      case REFUSAL -> throw new ModelRefusedException("fake-refusal");
      case SCHEMA_INVALID -> invalid(request);
      case INVALID_THEN_VALID ->
          invalidOnceServed.add(request.promptId()) ? invalid(request) : null;
    };
  }

  private ChatResponse invalid(ChatRequest request) {
    JsonNode output = mapper.createObjectNode().put("__schemaInvalid", true);
    return new ChatResponse(output, request.modelId(), TokenUsage.NONE, true);
  }

  private ChatResponse replay(ChatRequest request, String caseId) {
    String base = root + "/" + request.promptId() + "/v" + request.promptVersion() + "/" + caseId;
    // A recorded analysis makes many calls of one prompt, so recordings are addressed by the hash
    // of the request (a changed prompt or input finds no file); a hand-made fixture is one file.
    String hashed = base + "/" + request.renderedSha256().substring(0, 16) + ".json";
    String location = classLoader.getResource(hashed) != null ? hashed : base + ".json";
    JsonNode fixture = read(location, request, caseId);
    List<String> problems = checkFixture(fixture, request.promptId(), request.promptVersion());
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Invalid fixture " + location + ": " + problems);
    }
    String expectedSha = fixture.path("requestSha256").asText();
    if (!expectedSha.equals(request.renderedSha256())) {
      throw new IllegalStateException(
          "Fixture "
              + location
              + " was recorded from a different request: prompt or input changed, re-record with"
              + " ./gradlew recordFixtures -Pcase="
              + caseId);
    }
    JsonNode usage = fixture.path("usage");
    return new ChatResponse(
        fixture.get("response"),
        fixture.get("modelId").asText(),
        new TokenUsage(
            usage.path("inputTokens").asLong(),
            usage.path("outputTokens").asLong(),
            usage.path("cacheReadTokens").asLong()),
        true);
  }

  private JsonNode read(String location, ChatRequest request, String caseId) {
    InputStream stream = classLoader.getResourceAsStream(location);
    if (stream == null) {
      throw new MissingFixtureException(request.promptId(), request.promptVersion(), caseId);
    }
    try (stream) {
      return mapper.readTree(stream);
    } catch (IOException e) {
      throw new UncheckedIOException("Unreadable fixture " + location, e);
    }
  }

  /**
   * Structural and schema checks shared with the fixture scanner. Returns a list of problems.
   *
   * @param schemas validator that can resolve {@code schemas/llm/{id}.v{n}.schema.json}
   */
  public static List<String> checkFixture(
      JsonNode fixture, String promptId, int promptVersion, JsonSchemas schemas) {
    List<String> problems = new java.util.ArrayList<>();
    if (fixture.path("fixtureVersion").asInt() != FIXTURE_VERSION) {
      problems.add("fixtureVersion must be " + FIXTURE_VERSION);
    }
    if (!promptId.equals(fixture.path("promptId").asText())) {
      problems.add("promptId does not match the path");
    }
    if (fixture.path("promptVersion").asInt() != promptVersion) {
      problems.add("promptVersion does not match the path");
    }
    for (String field : List.of("caseId", "modelId", "recordedAt")) {
      if (fixture.path(field).asText().isBlank()) {
        problems.add(field + " is required");
      }
    }
    if (!fixture.path("requestSha256").asText().matches("[0-9a-f]{64}")) {
      problems.add("requestSha256 must be 64 lower-case hex characters");
    }
    if (!PROVENANCES.contains(fixture.path("provenance").asText())) {
      problems.add("provenance must be one of " + PROVENANCES);
    }
    JsonNode response = fixture.get("response");
    if (response == null || response.isNull()) {
      problems.add("response is required");
    } else {
      String schema = schemaLocation(promptId, promptVersion);
      if (!schemas.exists(schema)) {
        problems.add("no output schema at " + schema);
      } else {
        schemas.validate(schema, response).forEach(error -> problems.add("response: " + error));
      }
    }
    return problems;
  }

  private List<String> checkFixture(JsonNode fixture, String promptId, int promptVersion) {
    return checkFixture(fixture, promptId, promptVersion, schemas);
  }

  public static String schemaLocation(String promptId, int promptVersion) {
    return "schemas/llm/" + promptId + ".v" + promptVersion + ".schema.json";
  }

  private static String stripSlashes(String value) {
    String result = value.startsWith("classpath:") ? value.substring("classpath:".length()) : value;
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
