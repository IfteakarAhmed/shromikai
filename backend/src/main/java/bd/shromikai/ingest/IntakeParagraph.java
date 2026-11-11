package bd.shromikai.ingest;

import bd.shromikai.shared.SensitiveText;

/**
 * One paragraph of the document, {@code p1}…{@code pn} in order. The text is held as {@link
 * SensitiveText} (R-02).
 *
 * @param lowOcrConfidence the page it came from was read by OCR with mean confidence below 80
 * @param legacyFont the text was converted from a legacy Bangla font encoding
 */
public record IntakeParagraph(
    String id, int page, SensitiveText text, boolean lowOcrConfidence, boolean legacyFont) {}
