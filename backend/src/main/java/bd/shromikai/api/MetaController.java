package bd.shromikai.api;

import bd.shromikai.law.CorpusStatsService;
import bd.shromikai.shared.config.ShromikaiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E-01 {@code GET /api/v1/meta}: build version, model IDs per role and corpus counts (FR-01). */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

  static final String NAME = "ShromikAI";

  private final CorpusStatsService corpus;
  private final ShromikaiProperties.Models models;
  private final String version;

  public MetaController(
      CorpusStatsService corpus,
      ShromikaiProperties properties,
      ObjectProvider<BuildProperties> buildProperties) {
    this.corpus = corpus;
    this.models = properties.models();
    BuildProperties build = buildProperties.getIfAvailable();
    this.version = build == null ? "unknown" : build.getVersion();
  }

  @GetMapping("/meta")
  public Meta meta() {
    return new Meta(
        NAME,
        version,
        new Meta.Models(
            models.reasoning(),
            models.fast(),
            models.verifierOrNull(),
            models.verifierFallback(),
            models.judgeOrNull(),
            models.localPii(),
            models.embedding()),
        Meta.Corpus.from(corpus.current()),
        null);
  }
}
