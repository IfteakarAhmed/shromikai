package bd.shromikai.analysis;

import bd.shromikai.privacy.PlaceholderMap;
import bd.shromikai.privacy.PrivacyShield;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.EgressContext;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Local redaction of a free-text claim that did not come through contract intake: the {@code claim}
 * of MCP {@code verify_legal_citation} (architecture §10.7, FR-64). The deterministic detectors and
 * {@code pii-extract} on the local model run first; the result carries typed placeholders and the
 * call's {@link EgressContext}, so the egress guard can check the outgoing payload against the
 * original values (R-03). If the local model is down the exception of {@code PrivacyShield} ({@code
 * local-model-unavailable}) propagates and nothing leaves the box.
 */
@Service
public final class ClaimRedactionService {

  private final PrivacyShield shield;

  public ClaimRedactionService(PrivacyShield shield) {
    this.shield = shield;
  }

  /** A redacted claim; closing it forgets the placeholder map. */
  public static final class RedactedClaim implements AutoCloseable {

    private final SensitiveText text;
    private final String language;
    private final PlaceholderMap map;

    private RedactedClaim(SensitiveText text, String language, PlaceholderMap map) {
      this.text = text;
      this.language = language;
      this.map = map;
    }

    public SensitiveText text() {
      return text;
    }

    /** {@code bn} when the claim contains Bangla script, else {@code en}. */
    public String language() {
      return language;
    }

    /** What the egress guard compares outgoing payloads with. */
    public EgressContext context() {
      return map;
    }

    @Override
    public void close() {
      map.clear();
    }

    @Override
    public String toString() {
      return "RedactedClaim[masked]";
    }
  }

  public RedactedClaim redact(String claim) {
    PlaceholderMap map = new PlaceholderMap();
    String text = shield.redact(List.of(claim), map).get(0).text().reveal();
    return new RedactedClaim(SensitiveText.of(text), language(claim), map);
  }

  private static String language(String text) {
    return text.codePoints()
            .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.BENGALI)
        ? "bn"
        : "en";
  }
}
