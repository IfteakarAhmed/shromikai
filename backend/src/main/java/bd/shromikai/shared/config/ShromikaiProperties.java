package bd.shromikai.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
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
    @Valid @NotNull FakeModels fakeModels,
    @Valid @NotNull Auth auth,
    @Valid @NotNull Quotas quotas) {

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

  /**
   * A cloud provider endpoint. An empty API key leaves the adapter unconfigured. {@link
   * #toString()} masks the key, so printing the settings (or any record that holds them) never
   * leaks it.
   */
  public record Provider(@Nullable String apiKey, @NotBlank String baseUrl) {

    public boolean configured() {
      return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
      return "Provider[apiKey="
          + (configured() ? "[MASKED]" : "[UNSET]")
          + ", baseUrl="
          + baseUrl
          + "]";
    }
  }

  /** Recorded-response replay for the {@code test} and {@code fake-models} profiles. */
  public record FakeModels(@NotBlank String fixtures, @NotBlank String defaultCase) {}

  /**
   * Identity settings (FR-05 to FR-11, ADR-0010). {@link #toString()} masks both secrets, so
   * printing the settings never leaks them.
   */
  public record Auth(
      @NotBlank String jwtSecret,
      @NotBlank String issuer,
      @NotNull Duration accessTokenTtl,
      @NotNull Duration refreshTokenTtl,
      @NotNull Duration guestTokenTtl,
      @Valid @NotNull Lockout lockout,
      @Valid @NotNull RateLimit rateLimit,
      @Min(4) @Max(16) int bcryptStrength,
      boolean cookieSecure,
      boolean hsts,
      @Nullable String demoPassword) {

    public boolean demoPasswordConfigured() {
      return demoPassword != null && demoPassword.length() >= 12;
    }

    @Override
    public String toString() {
      return "Auth[jwtSecret=[MASKED], issuer="
          + issuer
          + ", accessTokenTtl="
          + accessTokenTtl
          + ", refreshTokenTtl="
          + refreshTokenTtl
          + ", guestTokenTtl="
          + guestTokenTtl
          + ", lockout="
          + lockout
          + ", rateLimit="
          + rateLimit
          + ", bcryptStrength="
          + bcryptStrength
          + ", cookieSecure="
          + cookieSecure
          + ", hsts="
          + hsts
          + ", demoPassword=[MASKED]]";
    }
  }

  /** Account lockout after {@code maxFailures} consecutive failed logins. */
  public record Lockout(@Min(1) int maxFailures, @NotNull Duration duration) {}

  /** Bucket4j limits on auth endpoints, per minute. */
  public record RateLimit(@Min(1) int perIpPerMinute, @Min(1) int perUserPerMinute) {}

  /** Guest quotas (FR-09); days are counted in {@code timezone}. */
  public record Quotas(
      @Min(1) int guestPerDevicePerDay,
      @Min(1) int guestPerIpPerDay,
      @Min(1) int guestConcurrency,
      @NotNull ZoneId timezone) {}
}
