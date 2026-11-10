package bd.shromikai.testsupport.archsamples.law;

import bd.shromikai.testsupport.archsamples.eval.EvalHarness;

/** Nothing may depend on eval (rule 7). */
public final class UsesEval {
  public EvalHarness harness() {
    return new EvalHarness();
  }
}
