package bd.shromikai.shared.problem;

import java.util.List;

/**
 * A {@code validation-failed} problem (400) with the pointers of the offending fields, for checks
 * that bean validation cannot express (a unit that does not fit a fact). {@code errors[]} in the
 * response lists {@code pointer} and {@code message} of each (api.md §7).
 */
public final class ValidationProblemException extends ProblemException {

  /** One field problem; the message never holds user content. */
  public record Error(String pointer, String message) {}

  private final transient List<Error> errors;

  public ValidationProblemException(List<Error> errors) {
    super(ProblemType.VALIDATION_FAILED);
    this.errors = List.copyOf(errors);
  }

  public List<Error> errors() {
    return errors;
  }
}
