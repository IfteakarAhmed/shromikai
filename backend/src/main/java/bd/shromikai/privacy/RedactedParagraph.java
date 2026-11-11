package bd.shromikai.privacy;

import bd.shromikai.shared.SensitiveText;

/** One redacted paragraph and whether it looks like an instruction to the model (FR-34). */
public record RedactedParagraph(SensitiveText text, boolean instructionLike) {}
