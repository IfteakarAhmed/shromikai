package bd.shromikai.law;

import bd.shromikai.law.internal.CorpusStatsRepository;
import org.springframework.stereotype.Service;

/** Read service for corpus counts, used by {@code GET /api/v1/meta} (FR-01). */
@Service
public final class CorpusStatsService {

  private final CorpusStatsRepository repository;

  public CorpusStatsService(CorpusStatsRepository repository) {
    this.repository = repository;
  }

  public CorpusStats current() {
    return repository.load();
  }
}
