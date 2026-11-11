package bd.shromikai.privacy;

import org.springframework.stereotype.Component;

/**
 * Puts the original values back into text for the owning subject (architecture §10.4). Called only
 * from {@code AnalysisService.resultFor}; stored traces and review items keep placeholders.
 */
@Component
public final class Reidentifier {

  public String restore(String redacted, PlaceholderMap map) {
    return map.restore(redacted);
  }
}
