package bd.shromikai.analysis;

import bd.shromikai.shared.Hashing;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who asked, as stored in {@code analysis_run}: a kind and a hash, never an identifier. The public
 * API (P7) derives it from the caller; the {@code dev} endpoint uses {@link #dev}.
 */
public record AnalysisSubject(String kind, String hash, @Nullable UUID orgId, String clientKind) {

  /** Identity for access checks: two requests are the same subject only when both parts match. */
  public String key() {
    return kind + ":" + hash;
  }

  public static AnalysisSubject dev() {
    return new AnalysisSubject("USER", Hashing.sha256Hex("dev"), null, "DESKTOP_WEB");
  }
}
