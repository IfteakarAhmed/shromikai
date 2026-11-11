package bd.shromikai.retrieval.internal;

import bd.shromikai.law.SectionKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Curated map from clause type to section keys ({@code data/mappings/bd-topics.yaml}, ADR-0004).
 * The file is data with author and reviewer recorded; keys are checked for shape here and resolved
 * to current sections at query time.
 */
@Component
public class TopicMap {

  private final Map<String, List<SectionKey>> byType;
  private final int max;

  @Autowired
  TopicMap(RetrievalProperties properties) throws IOException {
    this.max = properties.topicMapMax();
    Path file = Path.of(properties.topicMapFile());
    try (InputStream in = Files.newInputStream(file)) {
      this.byType = parse(in);
    }
  }

  TopicMap(InputStream yaml, int max) {
    this.max = max;
    this.byType = parse(yaml);
  }

  /** At most {@code topic-map-max} section keys mapped to the clause type; empty if unmapped. */
  public List<SectionKey> sectionsFor(String clauseType) {
    List<SectionKey> keys = byType.getOrDefault(clauseType, List.of());
    return keys.subList(0, Math.min(max, keys.size()));
  }

  public Map<String, List<SectionKey>> all() {
    return byType;
  }

  private static Map<String, List<SectionKey>> parse(InputStream in) {
    Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    if (!(loaded instanceof Map<?, ?> root) || !(root.get("topics") instanceof Map<?, ?> topics)) {
      throw new IllegalStateException("topic map needs a 'topics' mapping");
    }
    Map<String, List<SectionKey>> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> topic : topics.entrySet()) {
      String type = String.valueOf(topic.getKey());
      if (!(topic.getValue() instanceof Map<?, ?> body)
          || !(body.get("sections") instanceof List<?> sections)) {
        throw new IllegalStateException("topic " + type + " needs a 'sections' list");
      }
      List<SectionKey> keys = new ArrayList<>();
      for (Object section : sections) {
        keys.add(
            SectionKey.parse(String.valueOf(section))
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "topic " + type + " lists a malformed section key: " + section)));
      }
      out.put(type, List.copyOf(keys));
    }
    return Map.copyOf(out);
  }
}
