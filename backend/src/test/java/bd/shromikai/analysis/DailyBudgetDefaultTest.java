package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * FR-62: the daily budget of all analyses stays at the normal cap in the shipped configuration and
 * in the {@code prod} profile. Only {@code scripts/demo-backend.sh}, which removes the cloud API
 * keys so no real spend is possible, raises it for the local demo and the end-to-end run.
 */
@Tag("unit")
@Requirement("FR-62")
class DailyBudgetDefaultTest {

  private static final String KEY = "shromikai.budget.daily-usd";

  private static Object value(String file) throws IOException {
    Path path = Path.of("src/main/resources", file);
    if (!Files.exists(path)) {
      return null;
    }
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader().load(file, new FileSystemResource(path));
    for (PropertySource<?> source : sources) {
      Object found = source.getProperty(KEY);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  @Test
  void theShippedDefaultIsFiftyDollarsADay() throws IOException {
    assertThat(String.valueOf(value("application.yml"))).isEqualTo("50");
  }

  @Test
  void theProdProfileDoesNotRaiseIt() throws IOException {
    assertThat(value("application-prod.yml")).isNull();
  }

  @Test
  void onlyTheDemoScriptRaisesItAndItRemovesTheCloudKeysFirst() throws IOException {
    String script = Files.readString(Path.of("../scripts/demo-backend.sh"));
    assertThat(script).contains("SHROMIKAI_BUDGET_DAILYUSD");
    assertThat(script.indexOf("unset ANTHROPIC_API_KEY"))
        .isNotNegative()
        .isLessThan(script.indexOf("SHROMIKAI_BUDGET_DAILYUSD"));
    for (String other : List.of("../scripts/dev-up.sh", "../docker-compose.yml")) {
      Path path = Path.of(other);
      if (Files.exists(path)) {
        assertThat(Files.readString(path)).doesNotContain("BUDGET_DAILYUSD");
      }
    }
  }
}
