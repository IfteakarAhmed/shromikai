package bd.shromikai.verify.internal;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ModelException;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.ModelRefusedException;
import bd.shromikai.shared.ai.ModelRequestRejectedException;
import bd.shromikai.shared.ai.ModelTimeoutException;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.verify.SupportLabel;
import bd.shromikai.verify.VerifierIndependence;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Check (c), the independent support judgement (architecture §7.3, FR-44): Gemini Flash through
 * {@code verify-support} when a Gemini model is configured; on a provider failure, timeout or
 * refusal, or when none is configured, the separate Claude prompt {@code verify-support-claude}
 * (flagged {@code SAME_FAMILY}). Malformed output after the gateway's one repair counts as {@code
 * UNRELATED}. If neither verifier can be reached the result is {@link Unavailable}, which the
 * decision table maps to {@code UNCERTAIN}, never to {@code VERIFIED}. The verifier receives the
 * claim, the quote and statute text only, never a contract clause (R-06, {@code
 * VerifierInputTest}).
 */
@Component
public final class SupportVerifier {

  static final String PRIMARY = "verify-support";
  static final String FALLBACK = "verify-support-claude";

  /** The result of the support check. */
  public sealed interface Outcome {}

  /** A model judged the claim. */
  public record Judged(
      SupportLabel label,
      double confidence,
      String reason,
      VerifierIndependence independence,
      String modelId,
      TokenUsage usage)
      implements Outcome {}

  /** No verifier could be reached. */
  public record Unavailable(TokenUsage usage) implements Outcome {}

  private final ModelGateway gateway;
  private final ShromikaiProperties.Models models;

  @Autowired
  public SupportVerifier(ModelGateway gateway, ShromikaiProperties properties) {
    this(gateway, properties.models());
  }

  public SupportVerifier(ModelGateway gateway, ShromikaiProperties.Models models) {
    this.gateway = gateway;
    this.models = models;
  }

  public Outcome verify(
      SensitiveText claim, String sectionText, @Nullable String translation, String quote) {
    Map<String, String> variables =
        Map.of(
            "claim",
            claim.reveal(),
            "quote",
            quote,
            "section_text",
            sectionText,
            "translation_text",
            translation == null || translation.isBlank() ? "none" : translation);
    if (models.verifierOrNull() != null) {
      try {
        return judged(gateway.call(PRIMARY, variables), VerifierIndependence.DIFFERENT_FAMILY);
      } catch (ModelOutputInvalidException e) {
        return malformed(VerifierIndependence.DIFFERENT_FAMILY, models.verifierOrNull());
      } catch (UpstreamUnavailableException
          | ModelTimeoutException
          | ModelRefusedException
          | ModelRequestRejectedException e) {
        // fall through to the Claude prompt
      }
    }
    try {
      return judged(gateway.call(FALLBACK, variables), VerifierIndependence.SAME_FAMILY);
    } catch (ModelOutputInvalidException e) {
      return malformed(VerifierIndependence.SAME_FAMILY, models.verifierFallback());
    } catch (UpstreamUnavailableException
        | ModelTimeoutException
        | ModelRefusedException
        | ModelRequestRejectedException e) {
      return new Unavailable(TokenUsage.NONE);
    } catch (ModelException other) {
      // An egress block or any other policy failure must stop the analysis, not be absorbed.
      throw other;
    }
  }

  private static Judged judged(ModelGateway.Result result, VerifierIndependence independence) {
    JsonNode out = result.output();
    SupportLabel label = SupportLabel.valueOf(out.path("label").asText());
    double confidence = Math.max(0.0, Math.min(1.0, out.path("confidence").asDouble(0)));
    String reason = out.path("reason").asText("");
    if (reason.length() > 200) {
      reason = reason.substring(0, 200);
    }
    return new Judged(label, confidence, reason, independence, result.modelId(), result.usage());
  }

  private static Judged malformed(VerifierIndependence independence, @Nullable String modelId) {
    return new Judged(
        SupportLabel.UNRELATED,
        0.0,
        "malformed verifier output",
        independence,
        modelId == null ? "unknown" : modelId,
        TokenUsage.NONE);
  }
}
