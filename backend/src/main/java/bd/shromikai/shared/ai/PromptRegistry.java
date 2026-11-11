package bd.shromikai.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The only reader of {@code prompts/} on the classpath (R-05). Loads the latest non-deprecated
 * version of a prompt, or a given version, with its front matter, body and output schema.
 */
@Component
public final class PromptRegistry {

  private static final Pattern FILE = Pattern.compile(".*/v([1-9][0-9]*)\\.md");
  private static final Pattern SYSTEM = Pattern.compile("<system>(.*)</system>", Pattern.DOTALL);
  private static final Pattern USER = Pattern.compile("<user>(.*)</user>", Pattern.DOTALL);

  private final ObjectMapper mapper;
  private final PathMatchingResourcePatternResolver resolver =
      new PathMatchingResourcePatternResolver();
  private final Map<String, Prompt> cache = new ConcurrentHashMap<>();

  public PromptRegistry(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** The highest version of the prompt whose status is not {@code deprecated}. */
  public Prompt latest(String id) {
    int best = 0;
    for (int version : versions(id)) {
      if (version > best && !"deprecated".equals(get(id, version).status())) {
        best = version;
      }
    }
    if (best == 0) {
      throw new IllegalArgumentException("No usable prompt " + id);
    }
    return get(id, best);
  }

  public Prompt get(String id, int version) {
    return cache.computeIfAbsent(id + "/v" + version, key -> load(id, version));
  }

  private List<Integer> versions(String id) {
    try {
      List<Integer> found = new ArrayList<>();
      for (Resource resource : resolver.getResources("classpath*:prompts/" + id + "/v*.md")) {
        Matcher m = FILE.matcher(resource.getURL().toString());
        if (m.matches()) {
          found.add(Integer.parseInt(m.group(1)));
        }
      }
      return found;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Prompt load(String id, int version) {
    String text = read("prompts/" + id + "/v" + version + ".md");
    int end = text.indexOf("\n---\n", 4);
    if (!text.startsWith("---\n") || end < 0) {
      throw new IllegalStateException("Prompt " + id + " v" + version + " has no front matter");
    }
    Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text.substring(4, end));
    if (!(loaded instanceof Map<?, ?> front)) {
      throw new IllegalStateException("Prompt " + id + " front matter must be a mapping");
    }
    String body = text.substring(end + 5);
    String schemaLocation = String.valueOf(front.get("output_schema"));
    JsonNode schema;
    try (InputStream in = classLoader().getResourceAsStream(schemaLocation)) {
      if (in == null) {
        throw new IllegalStateException("No output schema " + schemaLocation);
      }
      schema = mapper.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<Prompt.Variable> variables = new ArrayList<>();
    for (Object entry : (List<?>) front.get("input_variables")) {
      Map<?, ?> variable = (Map<?, ?>) entry;
      variables.add(
          new Prompt.Variable(
              String.valueOf(variable.get("name")),
              "json".equals(variable.get("type")),
              "untrusted".equals(variable.get("trust"))));
    }
    Object temperature = front.get("temperature");
    Object effort = front.get("effort");
    return new Prompt(
        String.valueOf(front.get("id")),
        ((Number) front.get("version")).intValue(),
        String.valueOf(front.get("status")),
        ModelRole.valueOf(String.valueOf(front.get("model_role"))),
        temperature instanceof Number n ? n.doubleValue() : null,
        effort == null ? null : Effort.valueOf(String.valueOf(effort).toUpperCase()),
        ((Number) front.get("max_output_tokens")).intValue(),
        ((Number) front.get("timeout_seconds")).intValue(),
        List.copyOf(variables),
        schemaLocation,
        schema,
        section(SYSTEM, body, "system").strip(),
        section(USER, body, "user").strip());
  }

  private static String section(Pattern pattern, String body, String name) {
    Matcher m = pattern.matcher(body);
    if (!m.find()) {
      throw new IllegalStateException("Prompt body has no <" + name + "> element");
    }
    return m.group(1);
  }

  private String read(String location) {
    try (InputStream in = classLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new IllegalArgumentException("No prompt file " + location);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ClassLoader classLoader() {
    return PromptRegistry.class.getClassLoader();
  }
}
