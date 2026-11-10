package bd.shromikai.testsupport.archsamples.api;

import bd.shromikai.testsupport.archsamples.law.internal.LawInternal;

/** Reaches into another module's internal package (rule 1). */
public final class UsesLawInternal {
  public LawInternal internal() {
    return new LawInternal();
  }
}
