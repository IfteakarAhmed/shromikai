package bd.shromikai.rules;

/** A rule file is invalid; the application does not start (architecture §9.1, FR-36). */
public final class RuleLoadException extends RuntimeException {

  public RuleLoadException(String message) {
    super(message);
  }
}
