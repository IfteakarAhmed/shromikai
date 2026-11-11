package bd.shromikai.rules;

import org.jspecify.annotations.Nullable;

/**
 * A rule that a clause breaks, or a required fact that no clause states (architecture §9.2).
 *
 * @param clauseId the clause whose fact violates the rule; {@code null} for an omission
 * @param observed the stated value, {@code null} for an omission
 * @param omission true for {@code REQUIRED} (finding kind {@code OMISSION})
 */
public record RuleViolation(
    Rule rule,
    @Nullable String clauseId,
    @Nullable Double observed,
    @Nullable Unit observedUnit,
    boolean omission) {}
