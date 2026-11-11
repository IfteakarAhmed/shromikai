package bd.shromikai.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks a {@link PlaybookInput} (FR-57): sizes, a unit that is allowed for the fact, a value only
 * where the comparator needs one, and messages in both languages. Errors carry JSON pointers so a
 * client can mark the field; an unusable unit is reported at {@code /rules/{i}/unit}.
 */
@Component
public final class PlaybookValidator {

  public static final int MAX_RULES = 50;
  private static final Pattern SECTION_KEY =
      Pattern.compile("^[A-Za-z][A-Za-z0-9-]*:[0-9A-Za-z-]+@[A-Za-z0-9._-]+$");

  /**
   * @param clauseTypes the valid clause type names (the caller knows the enum)
   */
  public List<FieldError> validate(PlaybookInput input, Set<String> clauseTypes) {
    List<FieldError> errors = new ArrayList<>();
    String name = input.name() == null ? "" : input.name().strip();
    if (name.length() < 2 || name.length() > 80) {
      errors.add(new FieldError("/name", "must be 2 to 80 characters"));
    }
    if (!"WORKER".equals(input.side()) && !"EMPLOYER".equals(input.side())) {
      errors.add(new FieldError("/side", "must be WORKER or EMPLOYER"));
    }
    if (input.rules().isEmpty() || input.rules().size() > MAX_RULES) {
      errors.add(new FieldError("/rules", "must hold 1 to " + MAX_RULES + " rules"));
    }
    for (int i = 0; i < input.rules().size(); i++) {
      rule(input.rules().get(i), "/rules/" + i, clauseTypes, errors);
    }
    return errors;
  }

  private static void rule(
      PlaybookRule rule, String at, Set<String> clauseTypes, List<FieldError> errors) {
    if (rule == null) {
      errors.add(new FieldError(at, "must not be null"));
      return;
    }
    if (rule.clauseType() == null || !clauseTypes.contains(rule.clauseType())) {
      errors.add(new FieldError(at + "/clauseType", "is not a clause type"));
    }
    if (rule.fact() == null) {
      errors.add(new FieldError(at + "/fact", "is required"));
    }
    if (rule.comparator() == null) {
      errors.add(new FieldError(at + "/comparator", "is required"));
      return;
    }
    boolean needsValue =
        rule.comparator() != RuleComparator.REQUIRED
            && rule.comparator() != RuleComparator.PROHIBITED;
    if (needsValue) {
      if (rule.value() == null
          || rule.value().isNaN()
          || rule.value().isInfinite()
          || rule.value() < 0) {
        errors.add(new FieldError(at + "/value", "must be a number of 0 or more"));
      }
      if (rule.unit() == null) {
        errors.add(new FieldError(at + "/unit", "is required"));
      } else if (rule.fact() != null && !rule.fact().allowedUnits().contains(rule.unit())) {
        errors.add(new FieldError(at + "/unit", rule.unit() + " is not a unit of " + rule.fact()));
      }
    } else if (rule.value() != null) {
      errors.add(new FieldError(at + "/value", "must be empty for " + rule.comparator()));
    }
    if (rule.severity() == null) {
      errors.add(new FieldError(at + "/severity", "is required"));
    }
    text(rule.messageBn(), at + "/messageBn", errors);
    text(rule.messageEn(), at + "/messageEn", errors);
    if (rule.statuteSectionKey() != null
        && !SECTION_KEY.matcher(rule.statuteSectionKey()).matches()) {
      errors.add(new FieldError(at + "/statuteSectionKey", "is not a section key"));
    }
  }

  private static void text(String value, String pointer, List<FieldError> errors) {
    if (value == null || value.isBlank() || value.length() > 300) {
      errors.add(new FieldError(pointer, "must be 1 to 300 characters"));
    }
  }
}
