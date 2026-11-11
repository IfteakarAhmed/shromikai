package bd.shromikai.rules;

import org.jspecify.annotations.Nullable;

/**
 * One policy rule of an organisation's playbook (FR-57): "in clauses of this type, this fact must
 * compare like this". The comparison has the semantics of a legal rule ({@link RuleEngine}), but a
 * deviation is a company-policy finding, never a legal one. {@code value} is {@code null} only for
 * {@code REQUIRED} and {@code PROHIBITED}; {@code unit} must be allowed for the fact (api.md §6.4).
 * The messages are written by the organisation.
 */
public record PlaybookRule(
    String clauseType,
    FactParameter fact,
    RuleComparator comparator,
    @Nullable Double value,
    @Nullable Unit unit,
    Severity severity,
    String messageBn,
    String messageEn,
    @Nullable String statuteSectionKey) {}
