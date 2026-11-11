package bd.shromikai.verify;

/** Where review items are kept for the human review queue (FR-50); metadata only (R-02). */
public interface ReviewItemStore {

  void save(ReviewItem item);

  /**
   * Gives every item of an analysis the review ticket the worker follows (FR-61): the ticket ID and
   * the SHA-256 of its secret token, never the token itself. A store that keeps nothing may ignore
   * it.
   */
  default void attachTicket(
      java.util.UUID analysisId, java.util.UUID ticketId, String tokenSha256) {}
}
