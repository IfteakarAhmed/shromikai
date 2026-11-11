package bd.shromikai.law.internal;

import org.jspecify.annotations.Nullable;

/**
 * An act of a jurisdiction pack (data/packs/{code}.yaml). {@code portalActId} is the numeric id on
 * bdlaws ({@code null} when the act is not published there); {@code pdfUrl} is the official PDF
 * used only when the HTML is unusable; {@code definitionsSection} names the section whose defined
 * terms feed {@code USES_DEFINITION}. The titles are labels written by the team, not statute text.
 */
record ActDefinition(
    String code,
    String jurisdiction,
    @Nullable String portalActId,
    @Nullable String pdfUrl,
    String authoritativeLang,
    String titleEn,
    @Nullable String titleBn,
    @Nullable String definitionsSection,
    boolean required,
    @Nullable String note) {}
