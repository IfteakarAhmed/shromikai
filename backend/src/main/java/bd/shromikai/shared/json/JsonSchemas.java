package bd.shromikai.shared.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads JSON Schema 2020-12 documents from the classpath and validates JSON against them (R-04).
 * Schemas are cached by classpath location.
 */
public final class JsonSchemas {

  private static final String URN_PREFIX = "urn:shromikai:schema:";

  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(
          SpecificationVersion.DRAFT_2020_12,
          builder ->
              builder
                  .schemaRegistryConfig(
                      SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build())
                  // api.md §5 schemas refer to each other as urn:shromikai:schema:{name}:{n}; the
                  // file is schemas/api/{name}.v{n}.schema.json on the classpath.
                  .schemaIdResolvers(
                      ids ->
                          ids.mappings(
                              id -> id.startsWith(URN_PREFIX), JsonSchemas::classpathLocation)));

  private final ClassLoader classLoader;
  private final Map<String, Schema> cache = new ConcurrentHashMap<>();

  public JsonSchemas() {
    this(JsonSchemas.class.getClassLoader());
  }

  public JsonSchemas(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /** Whether a schema exists at the classpath location, for example {@code schemas/llm/x.json}. */
  public boolean exists(String location) {
    return classLoader.getResource(location) != null;
  }

  /**
   * Validation errors of {@code document} against the schema at {@code location}; empty if valid.
   */
  public List<String> validate(String location, JsonNode document) {
    Schema schema = cache.computeIfAbsent(location, this::load);
    return schema.validate(document).stream().map(Error::getMessage).sorted().toList();
  }

  /**
   * Validates a schema given as JSON and returns it, for documents that are not on the classpath.
   */
  public static List<String> validate(JsonNode schema, JsonNode document) {
    return REGISTRY.getSchema(schema).validate(document).stream()
        .map(Error::getMessage)
        .sorted()
        .toList();
  }

  /** Validates a schema document against the 2020-12 meta-schema. */
  public static List<String> metaSchemaErrors(JsonNode schema) {
    Schema meta =
        REGISTRY.getSchema(
            com.networknt.schema.SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
    return meta.validate(schema).stream().map(Error::getMessage).sorted().toList();
  }

  private static String classpathLocation(String id) {
    String[] parts = id.substring(URN_PREFIX.length()).split(":");
    return "classpath:schemas/api/" + parts[0] + ".v" + parts[1] + ".schema.json";
  }

  private Schema load(String location) {
    InputStream stream =
        Optional.ofNullable(classLoader.getResourceAsStream(location))
            .orElseThrow(() -> new IllegalArgumentException("No JSON Schema at " + location));
    try (stream) {
      return REGISTRY.getSchema(stream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
