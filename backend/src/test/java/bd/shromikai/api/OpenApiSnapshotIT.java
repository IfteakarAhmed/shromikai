package bd.shromikai.api;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * The committed OpenAPI snapshot {@code frontend/src/api/openapi.json} matches the running API, so
 * generated frontend types cannot drift (AGENTS.md §6). Refresh with {@code ./gradlew
 * integrationTest --tests '*OpenApiSnapshotIT' -PupdateOpenApi}.
 */
@IntegrationTest
@Requirement("FR-01")
class OpenApiSnapshotIT {

  static final Path SNAPSHOT = Path.of("../frontend/src/api/openapi.json");

  @Autowired TestRestTemplate http;

  @Test
  void shouldMatchCommittedSnapshot() throws Exception {
    ObjectMapper mapper =
        new ObjectMapper()
            .enable(
                SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    JsonNode live = http.getForObject("/v3/api-docs", JsonNode.class);
    String rendered = mapper.writeValueAsString(mapper.treeToValue(live, Object.class)) + "\n";

    if (Boolean.getBoolean("shromikai.updateOpenApi")) {
      Files.writeString(SNAPSHOT, rendered);
    }

    assertThat(live.at("/paths/~1api~1v1~1meta/get").isMissingNode()).isFalse();
    assertThat(live.at("/paths").toString()).doesNotContain("test-probe");
    assertThat(Files.exists(SNAPSHOT))
        .as("run with -PupdateOpenApi to create the snapshot")
        .isTrue();
    assertThat(Files.readString(SNAPSHOT))
        .as("OpenAPI changed: run with -PupdateOpenApi, then npm run gen:api in frontend/")
        .isEqualTo(rendered);
  }
}
