package bd.shromikai.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application settings under {@code shromikai.*} (architecture §12). */
@Validated
@ConfigurationProperties("shromikai")
public record ShromikaiProperties(
    @Valid @NotNull Models models,
    @Valid @NotNull Privacy privacy,
    @Valid @NotNull Cors cors,
    @Valid @NotNull Ollama ollama,
    @Valid @NotNull Providers providers,
    @Valid @NotNull FakeModels fakeModels) {

  /** Model ID per model role (ADR-0005). Verifier and judge are empty without a Gemini model. */
  public record Models(
      @NotBlank String reasoning,
      @NotBlank String fast,
      @Nullable String verifier,
      @NotBlank String verifierFallback,
      @Nullable String judge,
      @NotBlank String localPii,
      @NotBlank String embedding) {

    public @Nullable String verifierOrNull() {
      return blankToNull(verifier);
    }

    public @Nullable String judgeOrNull() {
      return blankToNull(judge);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
      return value == null || value.isBlank() ? null : value;
    }
  }

  /** Privacy switches. {@code storeContracts} must stay false (R-02); startup fails otherwise. */
  public record Privacy(boolean storeContracts, boolean redactMoney) {}

  public record Cors(@NotEmpty List<String> allowedOrigins) {}

  public record Ollama(@NotBlank String baseUrl, @NotNull Duration healthTimeout) {}

  public record Providers(@Valid @NotNull Provider anthropic, @Valid @NotNull Provider gemini) {}

  /** A cloud provider endpoint. An empty API key leaves the adapter unconfigured. */
  public record Provider(@Nullable String apiKey, @NotBlank String baseUrl) {

    public boolean configured() {
      return apiKey != null && !apiKey.isBlank();
    }
  }

  /** Recorded-response replay for the {@code test} and {@code fake-models} profiles. */
  public record FakeModels(@NotBlank String fixtures, @NotBlank String defaultCase) {}
}
