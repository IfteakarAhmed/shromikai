package bd.shromikai.testsupport.archsamples.law;

import bd.shromikai.testsupport.archsamples.shared.SharedUsesLaw;

/** law may depend on shared; the back edge to SharedUsesLaw closes a cycle. */
public final class LawType {
  public SharedUsesLaw shared() {
    return new SharedUsesLaw();
  }
}
