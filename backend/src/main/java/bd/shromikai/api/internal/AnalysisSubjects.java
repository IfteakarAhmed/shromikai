package bd.shromikai.api.internal;

import bd.shromikai.analysis.AnalysisSubject;
import bd.shromikai.auth.AuthenticatedSubject;
import bd.shromikai.shared.Hashing;

/** Maps the authenticated caller to the subject an analysis belongs to (T-07). */
public final class AnalysisSubjects {

  private AnalysisSubjects() {}

  /** A guest is its device, a user is its account; the hash is all that is stored. */
  public static AnalysisSubject of(AuthenticatedSubject subject, String clientKind) {
    if (subject.guest()) {
      return new AnalysisSubject("GUEST", subject.deviceHash(), null, clientKind);
    }
    return new AnalysisSubject(
        "USER", Hashing.sha256Hex(String.valueOf(subject.userId())), subject.orgId(), clientKind);
  }
}
