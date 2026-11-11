package bd.shromikai.ingest;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.util.List;

/** One or more pages are below the OCR confidence threshold (FR-27): {@code pages} lists them. */
public final class UnreadableDocumentException extends ProblemException {

  public UnreadableDocumentException(List<Integer> pages) {
    super(
        ProblemType.UNREADABLE_DOCUMENT,
        "Pages that could not be read: " + pages + ". Retake the photo or scan these pages.",
        null,
        pages);
  }
}
