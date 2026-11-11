package bd.shromikai.law;

import bd.shromikai.law.internal.LawReadRepository;
import bd.shromikai.law.internal.PackCatalogReader;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Read access to the statute corpus for the law API and MCP (FR-22). */
@Service
public class LawReadService {

  private final LawReadRepository repository;
  private final CorpusStatsService stats;
  private final PackCatalogReader packs;

  LawReadService(LawReadRepository repository, CorpusStatsService stats, PackCatalogReader packs) {
    this.repository = repository;
    this.stats = stats;
    this.packs = packs;
  }

  /** The jurisdiction packs and their coverage (MCP {@code list_jurisdiction_packs}). */
  public List<PackInfo> packs() {
    return packs.packs();
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

  /**
   * Finds the exact section version a section key names, whether or not it is current, with the
   * act's jurisdiction and status; used by the citation verifier's existence check (FR-42).
   */
  public Optional<SectionLookup> lookup(String sectionKey) {
    return SectionKey.parse(sectionKey)
        .flatMap(
            key ->
                repository
                    .section(key.actCode(), key.sectionNo(), key.versionLabel(), corpusRelease())
                    .flatMap(
                        section ->
                            repository
                                .act(key.actCode())
                                .map(
                                    act ->
                                        new SectionLookup(
                                            section, act.jurisdiction(), act.status()))));
  }

  /** A section version with the jurisdiction and status of its act. */
  public record SectionLookup(Section section, String jurisdiction, String actStatus) {}

  /** Label of the latest corpus release, or {@code null} before the first ingestion. */
  public @Nullable String corpusRelease() {
    return stats.current().release();
  }
}
