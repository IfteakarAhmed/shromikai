package bd.shromikai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * FR-36: {@code DEMO_RULES=off} is a switch of {@code scripts/demo-backend.sh} only. It makes the
 * demo load no rule file (so the older recordings replay); the default is rules on. The script
 * does it with the environment variable below, which must really bind to an empty list, and no
 * production code or configuration reads {@code DEMO_RULES}.
 */
@Tag("unit")
@Requirement("FR-36")
class DemoRulesFlagTest {

  @Test
  void theScriptSwitchesTheRulesOffOnlyWhenAskedAndDefaultsToOn() throws IOException {
    String script = Files.readString(Path.of("../scripts/demo-backend.sh"));

    assertThat(script).contains("DEMO_RULES");
    assertThat(script).contains("\"${DEMO_RULES:-on}\" = \"off\"");
    assertThat(script).contains("SHROMIKAI_RULES_FILES=");
  }

  @Test
  void theEnvironmentVariableOfTheScriptBindsToNoRuleFile() {
    MutablePropertySources sources = new MutablePropertySources();
    sources.addFirst(
        new SystemEnvironmentPropertySource("env", Map.of("SHROMIKAI_RULES_FILES", "")));
    sources.addLast(
        new MapPropertySource("yaml", Map.of("shromikai.rules.files[0]", "data/rules/x.yaml")));

    RulesProperties bound =
        new Binder(ConfigurationPropertySources.from(sources))
            .bind("shromikai.rules", RulesProperties.class)
            .get();

    assertThat(bound.files()).isEmpty();
  }

  @Test
  void noProductionCodeOrConfigurationReadsTheFlag() throws IOException {
    try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        assertThat(Files.readString(file)).as(file.toString()).doesNotContain("DEMO_RULES");
      }
    }
    for (String other : new String[] {"../docker-compose.yml", "../docker-compose.prod.yml"}) {
      if (Files.exists(Path.of(other))) {
        assertThat(Files.readString(Path.of(other))).as(other).doesNotContain("DEMO_RULES");
      }
    }
  }
}
