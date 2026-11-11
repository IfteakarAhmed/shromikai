package bd.shromikai.memo;

/** Turns a self-contained HTML document into a PDF; the only implementation is Gotenberg. */
public interface PdfRenderer {

  /**
   * @throws bd.shromikai.shared.problem.ProblemException {@code renderer-unavailable} (503)
   */
  byte[] pdf(String html);
}
