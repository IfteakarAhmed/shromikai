package bd.shromikai.verify;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Verification thresholds (architecture §7.7). Changing a threshold needs an ADR and an evaluation
 * report; the defaults here are the specified values.
 */
@Validated
@ConfigurationProperties("shromikai.verify")
public record VerifyProperties(
    @Min(1) int quoteMinChars,
    @Min(1) int quoteMaxChars,
    @DecimalMin("0.5") @DecimalMax("1.0") double quoteSimilarityThreshold,
    @DecimalMin("0.0") @DecimalMax("1.0") double supportMinConfidence,
    @Min(0) int correctiveAttempts,
    @Min(1) int parallelism) {

  public static VerifyProperties defaults() {
    return new VerifyProperties(20, 300, 0.92, 0.70, 1, 8);
  }
}
