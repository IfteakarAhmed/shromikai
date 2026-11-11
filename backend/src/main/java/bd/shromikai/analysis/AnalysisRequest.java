package bd.shromikai.analysis;

import bd.shromikai.rules.Playbook;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the user asked for: the side, the language of the explanations ({@code bn} by default) and
 * an optional profile (sector, job role, reading level) and an optional playbook of the caller's
 * organisation. The profile changes wording only (FR-40); the playbook adds policy findings, kept
 * apart from legal ones (FR-57).
 */
public record AnalysisRequest(
    Side side, String outputLanguage, Map<String, String> profile, @Nullable Playbook playbook) {

  public AnalysisRequest(Side side, String outputLanguage, Map<String, String> profile) {
    this(side, outputLanguage, profile, null);
  }

  public AnalysisRequest {
    if (!"bn".equals(outputLanguage) && !"en".equals(outputLanguage)) {
      throw new IllegalArgumentException("outputLanguage must be bn or en");
    }
    profile = sanitised(profile);
  }

  /**
   * Profile values reach prompts as trusted text (FR-56), so free text cannot carry markup, line
   * breaks or control characters, and is at most 60 characters (R-06). Enumerated values (sector,
   * language, reading level) are validated at the API and are not changed by this.
   */
  private static Map<String, String> sanitised(Map<String, String> profile) {
    Map<String, String> out = new java.util.LinkedHashMap<>();
    profile.forEach(
        (key, value) -> {
          String clean = value.replaceAll("[<>&\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").strip();
          if (clean.length() > 60) {
            clean = clean.substring(0, 60).strip();
          }
          out.put(key, clean);
        });
    return Map.copyOf(out);
  }

  public static AnalysisRequest of(Side side) {
    return new AnalysisRequest(side, "bn", Map.of());
  }
}
