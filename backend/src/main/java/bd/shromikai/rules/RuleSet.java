package bd.shromikai.rules;

import java.util.List;

/**
 * The rules loaded at startup. Only active rules are ever evaluated; the loader already refused any
 * active rule without a second reviewer or with a quote that is not in the corpus (FR-36).
 */
public final class RuleSet {

  private final List<Rule> rules;
  private final List<String> draftProblems;

  public RuleSet(List<Rule> rules, List<String> draftProblems) {
    this.rules = List.copyOf(rules);
    this.draftProblems = List.copyOf(draftProblems);
  }

  public List<Rule> all() {
    return rules;
  }

  public List<Rule> active() {
    return rules.stream().filter(Rule::active).toList();
  }

  public long draftCount() {
    return rules.stream().filter(r -> !r.active()).count();
  }

  public List<String> draftProblems() {
    return draftProblems;
  }
}
