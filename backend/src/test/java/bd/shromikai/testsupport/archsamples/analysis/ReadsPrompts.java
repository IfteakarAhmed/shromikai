package bd.shromikai.testsupport.archsamples.analysis;

/** Reads a runtime prompt directly instead of through PromptRegistry (rule 4). */
public final class ReadsPrompts {
  public String location() {
    return "classpath:prompts/risk-analysis/v1.md";
  }
}
