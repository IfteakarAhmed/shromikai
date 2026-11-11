package bd.shromikai.privacy;

/** The kinds of personal identifier ShromikAI redacts (docs/spec/api.md §6.2). */
public enum PiiType {
  PERSON(false),
  ORG(false),
  ADDRESS(false),
  EMPLOYEE_ID(false),
  NID(true),
  PHONE(true),
  EMAIL(true),
  BANK_ACCOUNT(true),
  CARD_NUMBER(true),
  MOBILE_WALLET(true),
  DOB(true),
  PASSPORT(true),
  BIRTH_REG(true),
  TIN(true);

  private final boolean detectedByRule;

  PiiType(boolean detectedByRule) {
    this.detectedByRule = detectedByRule;
  }

  /** {@code true} if a deterministic detector finds it, {@code false} if only the local model. */
  public boolean detectedByRule() {
    return detectedByRule;
  }

  /** The placeholder stem, for example {@code PERSON} in {@code [PERSON_1]}. */
  public String stem() {
    return name();
  }
}
