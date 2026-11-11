package bd.shromikai.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The five synthetic contracts under {@code eval/contracts/synthetic/} (all invented, R-02). */
public final class SyntheticContracts {

  public static final List<String> IDS =
      List.of("syn-bn-01", "syn-bn-02", "syn-bn-03", "syn-en-01", "syn-en-02");

  private static final Path DIR = Path.of("..", "eval", "contracts", "synthetic");

  /** One entity of a contract: PiiType name and the exact value. */
  public record Entity(String type, String value) {}

  private SyntheticContracts() {}

  public static String text(String id) {
    try {
      return Files.readString(DIR.resolve(id + ".md"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static int expectedClauseCount(String id) {
    return ((Number) meta(id).get("expectedClauseCount")).intValue();
  }

  public static String marker(String id) {
    @SuppressWarnings("unchecked")
    Map<String, Object> canaries = (Map<String, Object>) meta(id).get("canaries");
    return (String) canaries.get("contentMarker");
  }

  public static List<Entity> entities(String id) {
    Map<String, Object> doc = yaml(DIR.resolve(id + ".entities.yaml"));
    List<Entity> out = new ArrayList<>();
    @SuppressWarnings("unchecked")
    List<Map<String, String>> list = (List<Map<String, String>>) doc.get("entities");
    list.forEach(e -> out.add(new Entity(e.get("type"), e.get("value"))));
    return out;
  }

  /** Values of the given PII types (names, NIDs, phones, e-mail addresses). */
  public static List<String> values(String id, String... types) {
    List<String> wanted = List.of(types);
    return entities(id).stream().filter(e -> wanted.contains(e.type())).map(Entity::value).toList();
  }

  private static Map<String, Object> meta(String id) {
    return yaml(DIR.resolve(id + ".meta.yml"));
  }

  private static Map<String, Object> yaml(Path path) {
    try {
      return new Yaml(new SafeConstructor(new LoaderOptions()))
          .load(Files.readString(path, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
