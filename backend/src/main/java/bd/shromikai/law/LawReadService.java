package bd.shromikai.law;

import bd.shromikai.law.internal.LawReadRepository;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Read access to the statute corpus for the law API and MCP (FR-22). */
@Service
public class LawReadService {

  private final LawReadRepository repository;
  private final CorpusStatsService stats;

  LawReadService(LawReadRepository repository, CorpusStatsService stats) {
    this.repository = repository;
    this.stats = stats;
  }

  public List<ActSummary> acts(List<String> jurisdictions) {
    return jurisdictions.isEmpty() ? List.of() : repository.acts(jurisdictions);
  }

  public Optional<Act> act(String actCode) {
    return repository
        .act(actCode)
        .map(summary -> Act.of(summary, repository.currentSections(actCode)));
  }

  /**
   * @param version a version label such as {@code original}; {@code null} for the current version
   */
  public Optional<Section> section(String actCode, String sectionNo, @Nullable String version) {
    return repository.section(actCode, sectionNo, version, corpusRelease());
  }

  /** Label of the latest corpus release, or {@code null} before the first ingestion. */
  public @Nullable String corpusRelease() {
    return stats.current().release();
  }
}
