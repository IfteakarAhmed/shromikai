package bd.shromikai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.testsupport.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Provider API keys never appear in {@code toString()} of the settings records (R-02, FR-03). */
@Tag("unit")
@Requirement("FR-03")
class ShromikaiPropertiesTest {

  static final String JWT_SECRET = "fictional-jwt-secret-for-tests-0123456789";
  static final String DEMO_PASSWORD = "fictional-demo-password-1";
  private static final String ANTHROPIC_KEY = "sk-ant-fictional-0001";
  private static final String GEMINI_KEY = "gemini-fictional-0002";

  @Test
  void shouldMaskApiKey_whenProviderIsPrinted() {
    ShromikaiProperties.Provider provider =
        new ShromikaiProperties.Provider(ANTHROPIC_KEY, "https://api.example.invalid");

    assertThat(provider.toString())
        .doesNotContain(ANTHROPIC_KEY)
        .contains("apiKey=[MASKED]", "baseUrl=https://api.example.invalid");
    assertThat(provider.apiKey()).isEqualTo(ANTHROPIC_KEY);
  }

  @Test
  void shouldShowUnsetKey_whenProviderHasNoKey() {
    assertThat(new ShromikaiProperties.Provider("", "http://a").toString())
        .contains("apiKey=[UNSET]");
    assertThat(new ShromikaiProperties.Provider(null, "http://a").toString())
        .contains("apiKey=[UNSET]");
  }

  @Test
  void shouldMaskApiKeys_whenParentRecordsArePrinted() {
    ShromikaiProperties.Providers providers =
        new ShromikaiProperties.Providers(
            new ShromikaiProperties.Provider(ANTHROPIC_KEY, "http://a"),
            new ShromikaiProperties.Provider(GEMINI_KEY, "http://g"));
    ShromikaiProperties properties =
        new ShromikaiProperties(
            new ShromikaiProperties.Models("r", "f", "", "vf", null, "l", "e"),
            new ShromikaiProperties.Privacy(false, false),
            new ShromikaiProperties.Cors(List.of("http://localhost:5173")),
            new ShromikaiProperties.Ollama("http://localhost:11434", Duration.ofSeconds(1)),
            providers,
            new ShromikaiProperties.FakeModels("fixtures/llm", "default"),
            ShromikaiPropertiesTestData.auth(JWT_SECRET),
            ShromikaiPropertiesTestData.quotas());

    assertThat(providers.toString()).doesNotContain(ANTHROPIC_KEY).doesNotContain(GEMINI_KEY);
    assertThat(properties.toString())
        .doesNotContain(ANTHROPIC_KEY)
        .doesNotContain(GEMINI_KEY)
        .doesNotContain(JWT_SECRET)
        .doesNotContain(DEMO_PASSWORD)
        .contains("apiKey=[MASKED]");
  }
}
