package bd.shromikai.law.internal;

import org.jspecify.annotations.Nullable;

/**
 * One footnote of a section. {@code amendingActId} is the portal id of the act the footnote links
 * to (for example {@code 1672}), {@code null} when the footnote links to no act.
 */
public record AmendmentNote(
    AmendmentKind kind,
    String text,
    @Nullable String amendingActId,
    @Nullable String amendingActTitle) {}
