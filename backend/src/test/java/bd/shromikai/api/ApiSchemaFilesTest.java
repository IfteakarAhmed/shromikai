package bd.shromikai.api;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-63: the response schemas of api.md §5 exist as JSON Schema 2020-12 files under {@code
 * schemas/api}, each named after its {@code $id}, and every cross-reference resolves. The OpenAPI
 * document and the contract tests are built from these files, so the frontend types and the real
 * responses cannot drift apart.
 */
@Tag("unit")
@Requirement("FR-63")
class ApiSchemaFilesTest {

  private static final Path DIR = Path.of("src/main/resources/schemas/api");
  private static final Pattern URN = Pattern.compile("urn:shromikai:schema:([a-z0-9-]+):(\\d+)");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static List<Path> files() throws IOException {
    try (Stream<Path> stream = Files.list(DIR)) {
      return stream
          .filter(p -> p.getFileName().toString().endsWith(".schema.json"))
          .sorted()
          .toList();
    }
  }

  @Test
  void everyApiSchemaIsValidAndNamedAfterItsId() throws IOException {
    assertThat(files()).hasSizeGreaterThanOrEqualTo(30);
    for (Path file : files()) {
      JsonNode schema = MAPPER.readTree(file.toFile());
      assertThat(JsonSchemas.metaSchemaErrors(schema)).as(file.toString()).isEmpty();
      Matcher id = URN.matcher(schema.path("$id").asText());
      assertThat(id.matches()).as("$id of %s", file).isTrue();
      assertThat(file.getFileName().toString())
          .isEqualTo(id.group(1) + ".v" + id.group(2) + ".schema.json");
    }
  }

  @Test
  void everyUrnReferenceResolvesToAFile() throws IOException {
    List<String> missing = new ArrayList<>();
    for (Path file : files()) {
      Matcher m = URN.matcher(Files.readString(file));
      while (m.find()) {
        if (!Files.exists(DIR.resolve(m.group(1) + ".v" + m.group(2) + ".schema.json"))) {
          missing.add(file.getFileName() + " -> " + m.group());
        }
      }
    }
    assertThat(new TreeSet<>(missing)).isEmpty();
  }

  @Test
  void aSchemaWithCrossReferencesValidatesThroughTheRegistry() {
    JsonNode bad = MAPPER.createObjectNode().put("parameter", "NOT_A_PARAMETER");
    List<String> errors = new JsonSchemas().validate("schemas/api/clause-fact.v1.schema.json", bad);
    assertThat(errors).isNotEmpty();
    JsonNode good =
        MAPPER
            .createObjectNode()
            .put("parameter", "NOTICE_BY_EMPLOYER")
            .put("value", 14)
            .put("unit", "DAYS")
            .put("confidence", 0.9)
            .put("regexAgrees", true);
    assertThat(new JsonSchemas().validate("schemas/api/clause-fact.v1.schema.json", good))
        .isEmpty();
  }
}
