package bd.shromikai.rules;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A typed fact read from a clause (api.md §5.2): derived data, not contract text.
 *
 * @param value {@code null} only when the clause mentions the parameter without a value; booleans
 *     are 0 or 1
 * @param confidence 0 to 1; lowered by 0.3 when the regex extractor disagrees (FR-35)
 * @param regexAgrees {@code null} when no regex extractor applies
 */
public record ClauseFact(
    FactParameter parameter,
    @Nullable Double value,
    Unit unit,
    double confidence,
    @Nullable Boolean regexAgrees) {

  public ClauseFact {
    Objects.requireNonNull(parameter, "parameter");
    Objects.requireNonNull(unit, "unit");
    if (!parameter.allowedUnits().contains(unit)) {
      throw new IllegalArgumentException(unit + " is not a unit of " + parameter);
    }
    if (confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("confidence must be between 0 and 1");
    }
  }
}
