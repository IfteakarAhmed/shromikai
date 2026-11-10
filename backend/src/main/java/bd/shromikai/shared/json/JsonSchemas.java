package bd.shromikai.shared.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
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

  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
  private static final SchemaValidatorsConfig CONFIG =
      SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

  private final ClassLoader classLoader;
  private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

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
    JsonSchema schema = cache.computeIfAbsent(location, this::load);
    return schema.validate(document).stream().map(ValidationMessage::getMessage).sorted().toList();
  }

  /**
   * Validates a schema given as JSON and returns it, for documents that are not on the classpath.
   */
  public static List<String> validate(JsonNode schema, JsonNode document) {
    return FACTORY.getSchema(schema, CONFIG).validate(document).stream()
        .map(ValidationMessage::getMessage)
        .sorted()
        .toList();
  }

  /** Validates a schema document against the 2020-12 meta-schema. */
  public static List<String> metaSchemaErrors(JsonNode schema) {
    JsonSchema meta =
        FACTORY.getSchema(
            com.networknt.schema.SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"),
            CONFIG);
    return meta.validate(schema).stream().map(ValidationMessage::getMessage).sorted().toList();
  }

  private JsonSchema load(String location) {
    InputStream stream =
        Optional.ofNullable(classLoader.getResourceAsStream(location))
            .orElseThrow(() -> new IllegalArgumentException("No JSON Schema at " + location));
    try (stream) {
      return FACTORY.getSchema(stream, CONFIG);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
