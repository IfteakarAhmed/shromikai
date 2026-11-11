package bd.shromikai.api;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-63: the OpenAPI document (committed snapshot, which {@code OpenApiSnapshotIT} keeps equal to
 * the running API) holds every path and method of the catalogue in api.md §3, except the endpoints
 * listed as not yet implemented. The list may only shrink.
 */
@Tag("unit")
@Requirement("FR-63")
class OpenApiContractTest {

  /** Catalogue entries without an implementation yet (compare is P7, the rest later phases). */
  private static final Set<String> NOT_YET =
      Set.of(
          //           // later phases (admin console, API keys, usage: P8/P9; account deletion
          // FR-78)
          "DELETE /api/v1/auth/me",
          "GET /api/v1/admin/users",
          "PATCH /api/v1/admin/users/{id}",
          "GET /api/v1/admin/api-keys",
          "DELETE /api/v1/admin/api-keys/{id}",
          "GET /api/v1/admin/usage",
          "GET /api/v1/admin/traces/{analysisId}",
          "POST /api/v1/admin/laws/check-hashes",
          "POST /api/v1/admin/laws/retrieval-smoke",
          "POST /api/v1/admin/review-items",
          // exist only under a profile or outside springdoc
          "POST /api/v1/auth/demo/{id}",
          "GET /actuator/health",
          "POST /api/v1/dev/analyses",
          "GET /api/v1/dev/analyses/{id}/findings",
          "GET /api/v1/dev/analyses/{id}/redacted");

  @Test
  void everyCataloguedEndpointIsInTheOpenApiDocumentExceptTheListedOnes() throws IOException {
    String catalogue = Files.readString(Path.of("../docs/spec/api.md"));
    JsonNode paths =
        new ObjectMapper()
            .readTree(Path.of("../frontend/src/api/openapi.json").toFile())
            .path("paths");
    Matcher m =
        Pattern.compile("\\| E-\\d+[a-z]? \\| `((?:GET|POST|PUT|PATCH|DELETE) [^`]+)`")
            .matcher(catalogue);
    Set<String> missing = new TreeSet<>();
    int checked = 0;
    while (m.find()) {
      String[] parts = m.group(1).split(" ", 2);
      String path = parts[1].replaceAll("\\s*\\(.*", "").trim();
      if (path.contains("{") && !paths.has(path)) {
        path = path.replaceAll("\\{[a-z]+}", "{id}");
      }
      checked++;
      if (!paths.path(path).has(parts[0].toLowerCase())) {
        missing.add(parts[0] + " " + path);
      }
    }
    assertThat(checked).isGreaterThan(30);
    assertThat(missing).isSubsetOf(NOT_YET);
  }
}
