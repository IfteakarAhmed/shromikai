package bd.shromikai.analysis;

import java.util.Map;

/**
 * What the user asked for: the side, the language of the explanations ({@code bn} by default) and
 * an optional profile (sector, job role, reading level). The profile changes wording only (FR-40).
 */
public record AnalysisRequest(Side side, String outputLanguage, Map<String, String> profile) {

  public AnalysisRequest {
    if (!"bn".equals(outputLanguage) && !"en".equals(outputLanguage)) {
      throw new IllegalArgumentException("outputLanguage must be bn or en");
    }
    profile = Map.copyOf(profile);
  }

  public static AnalysisRequest of(Side side) {
    return new AnalysisRequest(side, "bn", Map.of());
  }
}
