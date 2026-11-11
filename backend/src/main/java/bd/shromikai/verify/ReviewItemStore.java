package bd.shromikai.verify;

/** Where review items are kept for the human review queue (FR-50); metadata only (R-02). */
public interface ReviewItemStore {

  void save(ReviewItem item);
}
