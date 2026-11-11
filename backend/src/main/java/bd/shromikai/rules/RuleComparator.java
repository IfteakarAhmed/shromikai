package bd.shromikai.rules;

/** How a fact is compared with a rule's value (architecture §9.2; api.md "Comparator"). */
public enum RuleComparator {
  MIN,
  MAX,
  EQ,
  REQUIRED,
  PROHIBITED
}
