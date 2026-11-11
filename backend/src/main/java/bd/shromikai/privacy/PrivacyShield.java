package bd.shromikai.privacy;

import bd.shromikai.shared.SensitiveText;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Step {@code REDACT}: deterministic detectors plus local entity extraction, then typed
 * placeholders (FR-28, FR-29, FR-31). It runs before any cloud call; if the local model fails twice
 * it throws {@code LocalModelUnavailableException} and nothing has been sent anywhere.
 */
@Service
public final class PrivacyShield {

  private final LocalEntityExtractor extractor;
  private final Redactor redactor;
  private final InjectionDetector injection;

  public PrivacyShield(
      LocalEntityExtractor extractor, Redactor redactor, InjectionDetector injection) {
    this.extractor = extractor;
    this.redactor = redactor;
    this.injection = injection;
  }

  /**
   * Redacts the paragraphs into the given job map. The map belongs to the caller (the analysis job)
   * and must be cleared when the job ends; on any failure it is cleared here.
   */
  public List<RedactedParagraph> redact(List<String> paragraphs, PlaceholderMap map) {
    try {
      LocalEntities entities = extractor.extract(String.join("\n\n", paragraphs));
      List<String> redacted = redactor.redact(paragraphs, entities, map);
      List<RedactedParagraph> out = new ArrayList<>(redacted.size());
      for (String text : redacted) {
        out.add(new RedactedParagraph(SensitiveText.of(text), injection.isInstructionLike(text)));
      }
      return out;
    } catch (RuntimeException e) {
      map.clear();
      throw e;
    }
  }
}
