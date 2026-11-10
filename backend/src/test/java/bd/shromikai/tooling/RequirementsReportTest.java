package bd.shromikai.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Requirement("NFR-15")
class RequirementsReportTest {

  @TempDir Path root;

  @Test
  void shouldMapRequirementsToJavaAndFrontendTests() throws IOException {
    Path java = Files.createDirectories(root.resolve("java"));
    Path web = Files.createDirectories(root.resolve("web/src"));
    Files.writeString(java.resolve("AIT.java"), "@Requirement(\"FR-10\")\n@Requirement(\"FR-2\")");
    Files.writeString(web.resolve("App.test.tsx"), "it('FR-2 shows status', () => {})");

    Map<String, Set<String>> report = RequirementsReport.collect(java, root.resolve("web"));

    assertThat(report.keySet()).containsExactly("FR-2", "FR-10");
    assertThat(report.get("FR-2")).containsExactly("AIT.java", "App.test.tsx");
    assertThat(RequirementsReport.render(report)).contains("| FR-10 | AIT.java |");
  }

  @Test
  void shouldCoverEveryP1Requirement_inTheRepository() throws IOException {
    Map<String, Set<String>> report =
        RequirementsReport.collect(Path.of("src/test/java"), Path.of("../frontend/src"));

    assertThat(report).containsKeys("FR-01", "FR-02", "FR-03", "FR-04", "NFR-15", "NFR-18");
  }
}
