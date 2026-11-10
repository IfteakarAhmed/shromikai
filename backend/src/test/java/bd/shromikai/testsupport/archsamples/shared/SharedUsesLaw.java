package bd.shromikai.testsupport.archsamples.shared;

import bd.shromikai.testsupport.archsamples.law.LawType;

/** shared must not depend on law (matrix); together with LawType this forms a cycle. */
public final class SharedUsesLaw {
  public LawType law() {
    return new LawType();
  }
}
