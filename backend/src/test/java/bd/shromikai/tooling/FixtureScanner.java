package bd.shromikai.tooling;

import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.shared.logging.LogRedactor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * {@code ./gradlew checkFixtures} ({@code scripts/check-fixtures}): R-02 and test-strategy.md §13
 * for everything under {@code src/test/resources/fixtures/}.
 *
 * <ul>
 *   <li>Recorded model responses ({@code llm/{promptId}/v{n}/{case}.json}) pass the structural and
 *       schema checks of {@link FakeChatPort} and contain no canary value outside placeholder form.
 *   <li>Documents ({@code documents/**}) have a {@code *.meta.yml} sidecar with {@code provenance}
 *       ({@code synthetic} or {@code public-template}), {@code source} and {@code licence}.
 *   <li>No text file contains an e-mail, Bangladeshi mobile or NID match that is not listed in
 *       {@code fixtures/canaries.yml}. P5 extends this with the full {@code PiiDetector}.
 * </ul>
 */
public final class FixtureScanner {

  static final Set<String> PROVENANCES = Set.of("synthetic", "public-template");
  static final Set<String> TEXT_EXTENSIONS =
      Set.of(".json", ".yml", ".yaml", ".txt", ".md", ".html", ".csv", ".jsonl", ".xml");
  private static final Pattern LLM_PATH =
      Pattern.compile(
          "llm/([a-z0-9-]+)/v([1-9][0-9]*)/(?:([A-Za-z0-9._-]+)/([0-9a-f]{16})|([A-Za-z0-9._-]+))\\.json");

  private final Path testResources;
  private final Path mainResources;
  private final ObjectMapper mapper = new ObjectMapper();

  public FixtureScanner(Path testResources, Path mainResources) {
    this.testResources = testResources;
    this.mainResources = mainResources;
  }

  public static void main(String[] args) throws IOException {
    List<String> violations = new FixtureScanner(Path.of(args[0]), Path.of(args[1])).scan();
    if (!violations.isEmpty()) {
      violations.forEach(violation -> System.err.println("checkFixtures: " + violation));
      System.exit(1);
    }
    System.out.println("checkFixtures: OK");
  }

  public List<String> scan() throws IOException {
    Path fixtures = testResources.resolve("fixtures");
    List<String> violations = new ArrayList<>();
    if (!Files.isDirectory(fixtures)) {
      return violations;
    }
    Set<String> canaries = canaries(fixtures.resolve("canaries.yml"), violations);
    List<Path> files;
    try (Stream<Path> walk = Files.walk(fixtures)) {
      files = walk.filter(Files::isRegularFile).sorted().toList();
    }
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {testResources.toUri().toURL(), mainResources.toUri().toURL()}, null)) {
      JsonSchemas schemas = new JsonSchemas(loader);
      for (Path file : files) {
        String relative = fixtures.relativize(file).toString().replace('\\', '/');
        if (relative.equals("canaries.yml")
            || relative.equals(".gitkeep")
            || relative.endsWith("/.gitkeep")) {
          continue;
        }
        if (relative.startsWith("llm/")) {
          checkLlmFixture(relative, file, schemas, canaries, violations);
        } else if (relative.startsWith("documents/") && !relative.endsWith(".meta.yml")) {
          checkDocument(relative, file, violations);
        }
        if (isText(file) && !relative.startsWith("llm/")) {
          checkPii(relative, Files.readString(file, StandardCharsets.UTF_8), canaries, violations);
        }
      }
    }
    return violations;
  }

  private void checkLlmFixture(
      String relative,
      Path file,
      JsonSchemas schemas,
      Set<String> canaries,
      List<String> violations)
      throws IOException {
    Matcher path = LLM_PATH.matcher(relative);
    if (!path.matches()) {
      violations.add(
          relative
              + ": model fixtures must be llm/{promptId}/v{n}/{caseId}.json or"
              + " llm/{promptId}/v{n}/{caseId}/{requestSha16}.json");
      return;
    }
    JsonNode fixture;
    try {
      fixture = mapper.readTree(file.toFile());
    } catch (IOException e) {
      violations.add(relative + ": not valid JSON");
      return;
    }
    FakeChatPort.checkFixture(fixture, path.group(1), Integer.parseInt(path.group(2)), schemas)
        .forEach(problem -> violations.add(relative + ": " + problem));
    String caseId = path.group(3) != null ? path.group(3) : path.group(5);
    if (!caseId.equals(fixture.path("caseId").asText())) {
      violations.add(relative + ": caseId does not match the file name");
    }
    if (path.group(4) != null
        && !fixture.path("requestSha256").asText().startsWith(path.group(4))) {
      violations.add(relative + ": file name is not the start of requestSha256");
    }
    StringBuilder text = new StringBuilder();
    collectStrings(fixture, text);
    String response = fixture.path("response").toString();
    for (String canary : canaries) {
      if (response.contains(canary)) {
        violations.add(relative + ": response contains a canary value outside placeholder form");
      }
    }
    checkPii(relative, text.toString(), canaries, violations);
  }

  /** String values of a fixture, without the request hash (hex digits look like numbers). */
  private static void collectStrings(JsonNode node, StringBuilder out) {
    if (node.isObject()) {
      node.properties()
          .forEach(
              entry -> {
                if (!entry.getKey().equals("requestSha256")) {
                  collectStrings(entry.getValue(), out);
                }
              });
    } else if (node.isArray()) {
      node.forEach(child -> collectStrings(child, out));
    } else if (node.isTextual()) {
      out.append(node.asText()).append('\n');
    }
  }

  private static void checkDocument(String relative, Path file, List<String> violations)
      throws IOException {
    Path sidecar = file.resolveSibling(file.getFileName() + ".meta.yml");
    if (!Files.isRegularFile(sidecar)) {
      violations.add(relative + ": missing sidecar " + sidecar.getFileName());
      return;
    }
    Object loaded =
        new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(sidecar));
    if (!(loaded instanceof Map<?, ?> meta)) {
      violations.add(relative + ": sidecar must be a mapping");
      return;
    }
    if (!PROVENANCES.contains(String.valueOf(meta.get("provenance")))) {
      violations.add(relative + ": provenance must be synthetic or public-template");
    }
    for (String key : List.of("source", "licence")) {
      if (!(meta.get(key) instanceof String value) || value.isBlank()) {
        violations.add(relative + ": sidecar needs " + key);
      }
    }
  }

  private static void checkPii(
      String relative, String text, Set<String> canaries, List<String> violations) {
    for (String match : LogRedactor.piiMatches(text)) {
      if (!canaries.contains(match)) {
        violations.add(relative + ": PII-like value not listed in canaries.yml");
      }
    }
  }

  private static Set<String> canaries(Path file, List<String> violations) throws IOException {
    Set<String> values = new LinkedHashSet<>();
    if (!Files.isRegularFile(file)) {
      violations.add("fixtures/canaries.yml is missing");
      return values;
    }
    Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
    if (loaded instanceof Map<?, ?> map && map.get("values") instanceof Map<?, ?> groups) {
      groups.values().forEach(group -> collect(group, values));
    } else {
      violations.add("fixtures/canaries.yml needs a values mapping");
    }
    return values;
  }

  private static void collect(Object node, Set<String> values) {
    if (node instanceof List<?> list) {
      list.forEach(item -> collect(item, values));
    } else if (node instanceof Map<?, ?> map) {
      map.values().forEach(item -> collect(item, values));
    } else if (node != null) {
      values.add(String.valueOf(node));
    }
  }

  private static boolean isText(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot));
  }
}
