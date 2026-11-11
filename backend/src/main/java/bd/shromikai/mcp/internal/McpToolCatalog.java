package bd.shromikai.mcp.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The five tool definitions of api.md §9, read from {@code mcp/tools/{name}.json} (input schema,
 * output schema, description and example are data, not code). The registered tools and {@code
 * McpToolSchemaTest} both use this catalog, so what a client sees is what the test checks.
 */
@Component
public final class McpToolCatalog {

  static final List<String> NAMES =
      List.of(
          "search_bd_law",
          "get_law_section",
          "verify_legal_citation",
          "analyze_employment_contract",
          "list_jurisdiction_packs");

  /** One tool: schemas are JSON Schema 2020-12 documents. */
  public record Definition(
      String name,
      String title,
      String description,
      JsonNode example,
      JsonNode input,
      JsonNode output) {}

  private final List<Definition> definitions;

  public McpToolCatalog(ObjectMapper mapper) {
    this.definitions = NAMES.stream().map(name -> load(mapper, name)).toList();
  }

  public List<Definition> definitions() {
    return definitions;
  }

  public Definition definition(String name) {
    return definitions.stream()
        .filter(d -> d.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No tool " + name));
  }

  private static Definition load(ObjectMapper mapper, String name) {
    String location = "mcp/tools/" + name + ".json";
    try (InputStream in = McpToolCatalog.class.getClassLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new IllegalStateException("Missing tool definition " + location);
      }
      JsonNode root = mapper.readTree(in);
      return new Definition(
          root.path("name").asText(),
          root.path("title").asText(),
          root.path("description").asText(),
          root.path("example"),
          root.path("input"),
          root.path("output"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
