package bd.shromikai.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.testsupport.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@Tag("unit")
@Requirement("NFR-05")
class StartupGuardsTest {

  @Test
  void shouldRefuseToStart_whenContractStorageIsOn() {
    assertThatThrownBy(() -> StartupGuards.check(new MockEnvironment(), properties(true)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("store-contracts");
  }

  @Test
  void shouldRefuseToStart_whenProdAndDemoAreActive() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod", "demo");

    assertThatThrownBy(() -> StartupGuards.check(environment, properties(false)))
        .hasMessageContaining("prod");
  }

  @Test
  void shouldStart_whenConfigurationIsSafe() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");

    assertThatCode(() -> StartupGuards.check(environment, properties(false)))
        .doesNotThrowAnyException();
  }

  static ShromikaiProperties properties(boolean storeContracts) {
    return new ShromikaiProperties(
        new ShromikaiProperties.Models("r", "f", "", "vf", null, "l", "e"),
        new ShromikaiProperties.Privacy(storeContracts, false),
        new ShromikaiProperties.Cors(List.of("http://localhost:5173")),
        new ShromikaiProperties.Ollama("http://localhost:11434", Duration.ofSeconds(1)),
        new ShromikaiProperties.Providers(
            new ShromikaiProperties.Provider("", "http://a"),
            new ShromikaiProperties.Provider(null, "http://g")),
        new ShromikaiProperties.FakeModels("fixtures/llm", "default"));
  }
}
