package bd.shromikai.ingest;

import java.util.List;

/**
 * Normalised paragraphs of one document with how each page was read.
 *
 * @param language {@code bn}, {@code en} or {@code mixed}
 */
public record IntakeResult(
    InputKind kind,
    int pageCount,
    String language,
    List<IntakeParagraph> paragraphs,
    List<PageQuality> pages) {

  public IntakeResult {
    paragraphs = List.copyOf(paragraphs);
    pages = List.copyOf(pages);
  }
}
