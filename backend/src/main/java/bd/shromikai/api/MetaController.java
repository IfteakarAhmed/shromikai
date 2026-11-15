package bd.shromikai.api;

import bd.shromikai.analysis.DemoModels;
import bd.shromikai.law.CorpusStatsService;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditService;
import bd.shromikai.verify.ReviewDesk;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-01 {@code GET /api/v1/meta}: build version, model IDs per role, corpus counts (FR-01) and the
 * audit counters of the live docs hub (FR-74).
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

  static final String NAME = "ShromikAI";

  private final CorpusStatsService corpus;
  private final AuditService audit;
  private final ReviewDesk reviews;
  private final ObjectProvider<DemoModels> demo;
  private final Clock clock;
  private final ShromikaiProperties.Models models;
  private final String version;

  public MetaController(
      CorpusStatsService corpus,
      AuditService audit,
      ReviewDesk reviews,
      ObjectProvider<DemoModels> demo,
      Clock clock,
      ShromikaiProperties properties,
      ObjectProvider<BuildProperties> buildProperties) {
    this.corpus = corpus;
    this.audit = audit;
    this.reviews = reviews;
    this.demo = demo;
    this.clock = clock;
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
        new Meta.Audit(
            audit.countSince(AuditAction.PRIVACY_BLOCK, clock.instant().minus(Duration.ofDays(7))),
            reviews.openCount(),
            null),
        demo.getIfAvailable() == null
            ? null
            : new Meta.RecordedDemo(DemoModels.RECORDED_SIDE.name(), DemoModels.RECORDED_LANGUAGE));
  }
}
