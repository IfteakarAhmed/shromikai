package bd.shromikai.testsupport.archsamples.api;

import bd.shromikai.testsupport.archsamples.verify.VerifiedCitation;

/** Constructs a VerifiedCitation outside verify (rule 2). */
public final class ForgesCitation {
  public VerifiedCitation forge() {
    return new VerifiedCitation();
  }
}
