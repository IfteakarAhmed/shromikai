package bd.shromikai.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The bundled synthetic sample contract, {@code syn-en-02} of the evaluation set: invented names,
 * team-authored, not a real contract (R-02, R-12). {@code POST /analyses} with {@code sample: true}
 * analyses it, so a visitor can see the whole flow without uploading anything. The build copies the
 * file from {@code eval/contracts/synthetic}, so there is one copy of the text (the recorded model
 * answers of the {@code fake-models} profile are keyed by it).
 */
@Component
public class SampleContract {

  /** ID of the sample in the evaluation set. */
  public static final String ID = "syn-en-02";

  private static final String TEXT_RESOURCE = "samples/" + ID + ".md";
  private static final String ENTITIES_RESOURCE = "samples/" + ID + ".entities.yaml";

  /** One personal value of the sample, as the local privacy model would find it. */
  public record Entity(String type, String value) {}

  private final String text = read(TEXT_RESOURCE);
  private final List<Entity> entities = loadEntities();

  /** The text, exactly as in the evaluation set. */
  public String text() {
    return text;
  }

  /** The invented personal values in the sample (ground truth of the evaluation set). */
  public List<Entity> entities() {
    return entities;
  }

  private static String read(String resource) {
    try (InputStream in = SampleContract.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<Entity> loadEntities() {
    Map<String, Object> doc =
        new Yaml(new SafeConstructor(new LoaderOptions())).load(read(ENTITIES_RESOURCE));
    List<Entity> out = new ArrayList<>();
    @SuppressWarnings("unchecked")
    List<Map<String, String>> list = (List<Map<String, String>>) doc.get("entities");
    list.forEach(e -> out.add(new Entity(e.get("type"), e.get("value"))));
    return List.copyOf(out);
  }
}
