package bd.shromikai.eval;

import org.jspecify.annotations.Nullable;

/**
 * One adversarial citation (evaluation.md §4.4). {@code expectedStatus} is {@code VERIFIED} for a
 * valid control and {@code NOT_VERIFIED} (REJECTED or UNCERTAIN) for every fabricated class.
 *
 * @param construction how the case was built, so a reviewer can check it
 * @param reviewedBy the person who checked the case; {@code null} until someone does
 */
public record AdversarialCase(
    String id,
    String cls,
    String sectionKey,
    String quote,
    String claim,
    String expectedStatus,
    String construction,
    @Nullable String reviewedBy) {}
