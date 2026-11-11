package bd.shromikai.law.internal;

import bd.shromikai.law.IloMappingException;
import bd.shromikai.law.IloMappingLoader;
import bd.shromikai.law.IngestOutcome;
import bd.shromikai.law.internal.DefinitionExtractor.Definition;
import bd.shromikai.law.internal.LawRepository.ActRow;
import bd.shromikai.law.internal.LawRepository.ReleaseRow;
import bd.shromikai.law.internal.LawRepository.SectionWrite;
import bd.shromikai.law.internal.VersionPlanner.Existing;
import bd.shromikai.law.internal.VersionPlanner.Incoming;
import bd.shromikai.law.internal.VersionPlanner.Plan;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.text.BanglaNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One ingestion run (FR-12 to FR-17): acquire every act politely, then, in one transaction, upsert
 * versions idempotently, rebuild the knowledge graph, load the ILO mapping, check quality and
 * create a corpus release. If the quality check fails nothing is committed (ADR-0011).
 */
@Component
public final class IngestOrchestrator {

  private static final Logger LOG = LoggerFactory.getLogger(IngestOrchestrator.class);
  private static final Pattern YEAR = Pattern.compile("(?<!\\d)(1[89]\\d\\d|20\\d\\d)(?!\\d)");
  private static final ObjectMapper JSON = new ObjectMapper();

  private final LawRepository repo;
  private final IngestProperties ingest;
  private final ScraperProperties scraper;
  private final Clock clock;
  private final TransactionTemplate tx;

  IngestOrchestrator(
      LawRepository repo,
      IngestProperties ingest,
      ScraperProperties scraper,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.repo = repo;
    this.ingest = ingest;
    this.scraper = scraper;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactions);
  }

  /** Thrown inside the transaction to roll it back when the quality report fails. */
  private static final class QualityFailed extends RuntimeException {
    private final transient QualityReport report;

    QualityFailed(QualityReport report) {
      super("corpus quality check failed", null, false, false);
      this.report = report;
    }
  }

  public IngestOutcome run(@Nullable Set<String> actFilter) {
    if (!scraper.hasContact()) {
      throw new IllegalStateException(
          "SCRAPER_CONTACT is not set: the scraper must identify itself (FR-12)");
    }
    PackManifest pack = PackManifest.load(ingest.packFile());
    List<ActDefinition> selected =
        pack.acts().stream()
            .filter(a -> actFilter == null || actFilter.isEmpty() || actFilter.contains(a.code()))
            .toList();
    if (selected.isEmpty()) {
      throw new IllegalArgumentException("no act of the pack matches " + actFilter);
    }
    ScraperSettings settings =
        new ScraperSettings(
            scraper.userAgent(),
            scraper.minInterval(),
            scraper.timeout(),
            2,
            java.time.Duration.ofMillis(500),
            scraper.rawDir(),
            scraper.cachePolicy());
    ActAcquirer acquirer =
        new ActAcquirer(
            new PoliteFetcher(settings, new RawCache(scraper.rawDir())), pack.portalBaseUrl());
    List<AcquiredAct> acquired = new ArrayList<>();
    for (ActDefinition def : selected) {
      LOG.info("acquiring act {}", def.code());
      acquired.add(acquirer.acquire(def));
    }

    long seed =
        ingest.sampleSeed() != null
            ? ingest.sampleSeed()
            : new SecureRandom().nextLong() & Long.MAX_VALUE;
    String gitSha = GitSha.current(ingest.reportDir().toAbsolutePath());
    List<IngestOutcome.ActSummary> summaries = new ArrayList<>();
    List<QualityReport.MissingLine> missing = new ArrayList<>();
    try {
      QualityReport report =
          tx.execute(
              status ->
                  persistAndRelease(pack, acquired, selected, summaries, missing, seed, gitSha));
      report.write(ingest.reportDir());
      LOG.info("ingestion succeeded, release {}", report.corpusRelease());
      return new IngestOutcome(
          "SUCCEEDED", report.corpusRelease(), summaries, List.of(), ingest.reportDir().toString());
    } catch (QualityFailed failed) {
      failed.report.write(ingest.reportDir());
      LOG.warn("ingestion failed the quality check, nothing committed");
      return new IngestOutcome(
          "FAILED",
          null,
          failedSummaries(acquired),
          failed.report.failures(),
          ingest.reportDir().toString());
    } catch (IloMappingException e) {
      QualityReport report = failureReport(pack, acquired, selected, e.getMessage(), seed, gitSha);
      report.write(ingest.reportDir());
      return new IngestOutcome(
          "FAILED",
          null,
          failedSummaries(acquired),
          report.failures(),
          ingest.reportDir().toString());
    }
  }

  // -------------------------------------------------------------------------------------------

  private QualityReport persistAndRelease(
      PackManifest pack,
      List<AcquiredAct> acquired,
      List<ActDefinition> selected,
      List<IngestOutcome.ActSummary> summaries,
      List<QualityReport.MissingLine> missing,
      long seed,
      String gitSha) {
    Instant at = clock.instant();
    Map<String, ActWrite> writes = new LinkedHashMap<>();
    long unresolved = 0;
    for (AcquiredAct a : acquired) {
      if (!a.ok()) {
        summaries.add(
            new IngestOutcome.ActSummary(
                a.definition().code(), a.sourceKind().name(), 0, 0, 0, 0, a.failure()));
        if (!a.definition().required() || a.failure().startsWith("NOT_PUBLISHED")) {
          missing.add(new QualityReport.MissingLine(a.definition().code(), a.failure()));
        }
        continue;
      }
      ActWrite w = persistAct(a, at);
      writes.put(a.definition().code(), w);
      unresolved += w.unresolved();
      summaries.add(
          new IngestOutcome.ActSummary(
              a.definition().code(),
              a.sourceKind().name(),
              a.act().sections().size(),
              w.inserted() + w.reactivated(),
              w.unchanged(),
              w.withdrawn().size(),
              null));
    }

    loadIloMapping(missing);
    List<QualityReport.IloLine> ilo = iloLines(pack);

    QualityReport.Input draft =
        reportInput(
            pack, acquired, selected, writes, missing, ilo, unresolved, null, seed, gitSha, at);
    QualityReport check = QualityReport.build(draft);
    if (check.failed()) {
      throw new QualityFailed(check);
    }
    ReleaseRow release = repo.createRelease();
    return QualityReport.build(
        reportInput(
            pack,
            acquired,
            selected,
            writes,
            missing,
            ilo,
            unresolved,
            release.label(),
            seed,
            gitSha,
            at));
  }

  private QualityReport failureReport(
      PackManifest pack,
      List<AcquiredAct> acquired,
      List<ActDefinition> selected,
      String reason,
      long seed,
      String gitSha) {
    QualityReport.Input in =
        reportInput(
            pack,
            acquired,
            selected,
            Map.of(),
            new ArrayList<>(),
            iloLines(pack),
            0,
            null,
            seed,
            gitSha,
            clock.instant());
    QualityReport base = QualityReport.build(in);
    List<String> failures = new ArrayList<>(base.failures());
    failures.add("ILO_MAPPING: " + reason);
    return new QualityReport(
        "FAILED",
        null,
        base.generatedAt(),
        base.gitSha(),
        base.sampleSeed(),
        base.acts(),
        base.ilo(),
        base.missing(),
        base.edges(),
        base.unresolvedReferences(),
        failures,
        base.samples());
  }

  private static List<IngestOutcome.ActSummary> failedSummaries(List<AcquiredAct> acquired) {
    return acquired.stream()
        .map(
            a ->
                new IngestOutcome.ActSummary(
                    a.definition().code(),
                    a.sourceKind().name(),
                    0,
                    0,
                    0,
                    0,
                    a.failure() == null ? "rolled back: quality check failed" : a.failure()))
        .toList();
  }

  private record ActWrite(
      int inserted,
      int reactivated,
      int unchanged,
      List<String> withdrawn,
      List<String> duplicates,
      long unresolved) {}

  private ActWrite persistAct(AcquiredAct acquired, Instant at) {
    ActDefinition def = acquired.definition();
    ParsedAct parsed = acquired.act();
    String lang = def.authoritativeLang();
    String title = parsed.title().isBlank() ? def.titleEn() : parsed.title();
    boolean repealed =
        title.contains("রহিত") || title.toLowerCase(java.util.Locale.ROOT).contains("[repealed]");
    long actId =
        repo.upsertAct(
            new ActRow(
                def.code(),
                lang.equals("en") && !parsed.title().isBlank() ? parsed.title() : def.titleEn(),
                lang.equals("bn")
                    ? (def.titleBn() != null ? def.titleBn() : blankToNull(parsed.title()))
                    : def.titleBn(),
                year(parsed.title() + " " + parsed.actNumberText()),
                def.jurisdiction(),
                acquired.sourceUrls().get(0),
                acquired.fetchedAt(),
                lang,
                repealed ? "REPEALED" : "IN_FORCE",
                acquired.sourceSha256(),
                null));

    Map<String, String> labels = AmendmentParser.labels(parsed.amendingActs());
    Map<String, List<Existing>> existing = repo.existingVersions(actId);
    Set<String> seen = new HashSet<>();
    List<String> duplicates = new ArrayList<>();
    int inserted = 0;
    int reactivated = 0;
    int unchanged = 0;
    List<ParsedSection> kept = new ArrayList<>();
    for (ParsedSection s : parsed.sections()) {
      if (!seen.add(s.sectionNo())) {
        duplicates.add(s.sectionNo());
        continue;
      }
      kept.add(s);
      String label =
          s.notes().isEmpty()
              ? AmendmentParser.ORIGINAL
              : AmendmentParser.latestLabel(s.notes(), labels);
      String hash = Hashing.sha256Hex(s.heading() + "\u0000" + s.text());
      Plan plan =
          VersionPlanner.plan(
              existing.getOrDefault(s.sectionNo(), List.of()),
              new Incoming(label, hash, !s.omitted()));
      switch (plan.action()) {
        case NOOP -> unchanged++;
        case REACTIVATE -> {
          repo.demote(plan.demote());
          repo.setCurrent(plan.reactivateId(), true);
          reactivated++;
        }
        case INSERT -> {
          repo.demote(plan.demote());
          long id =
              repo.insertSection(actId, write(def, acquired, s, plan.label(), hash, parsed, at));
          if (plan.amendedFrom() != null) {
            repo.insertEdge(plan.amendedFrom(), id, "AMENDED_BY", "PARSER", false, null);
          }
          inserted++;
        }
      }
    }
    Set<String> present = new HashSet<>();
    kept.forEach(s -> present.add(s.sectionNo()));
    List<String> withdrawn = repo.withdrawMissing(actId, present);
    repo.touchCurrent(actId, at);
    long unresolved = rebuildGraph(def, parsed.title(), actId, kept);
    return new ActWrite(inserted, reactivated, unchanged, withdrawn, duplicates, unresolved);
  }

  private SectionWrite write(
      ActDefinition def,
      AcquiredAct acquired,
      ParsedSection s,
      String label,
      String hash,
      ParsedAct parsed,
      Instant at) {
    boolean original = label.equals(AmendmentParser.ORIGINAL) || label.startsWith("original");
    String notes;
    try {
      notes =
          JSON.writeValueAsString(
              s.notes().stream()
                  .map(
                      n ->
                          Map.of(
                              "kind",
                              n.kind().name(),
                              "text",
                              n.text(),
                              "amendingAct",
                              n.amendingActId() == null ? "" : n.amendingActId()))
                  .toList());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    return new SectionWrite(
        s.sectionNo(),
        s.sectionNoOriginal(),
        s.heading(),
        s.text(),
        def.authoritativeLang(),
        label,
        original ? parsed.publishedOn() : null,
        !s.omitted(),
        s.sourceUrl(),
        hash,
        acquired.parserVersion(),
        s.chapterNo().isEmpty()
            ? s.chapterName()
            : s.chapterNo() + (s.chapterName().isEmpty() ? "" : " — " + s.chapterName()),
        acquired.fetchedAt(),
        BanglaNormalizer.matchingForm(s.heading() + " " + s.text()),
        def.code() + ":" + s.sectionNo() + "@" + label,
        notes);
  }

  /** Rebuilds REFERS_TO and USES_DEFINITION of the act from its current sections. */
  private long rebuildGraph(
      ActDefinition def, String parsedTitle, long actId, List<ParsedSection> sections) {
    repo.clearDerivedEdges(actId);
    repo.clearTerms(actId);
    Map<String, Long> ids = repo.currentIds(actId);
    Set<String> ownNames = new HashSet<>();
    ownNames.add(def.titleEn());
    if (def.titleBn() != null) {
      ownNames.add(def.titleBn());
    }
    ownNames.add(parsedTitle);
    ownNames.removeIf(String::isBlank);
    CrossReferenceExtractor extractor = new CrossReferenceExtractor(ownNames);
    long unresolved = 0;
    for (ParsedSection s : sections) {
      Long from = ids.get(s.sectionNo());
      if (from == null || s.omitted()) {
        continue;
      }
      for (CrossReferenceExtractor.Reference ref : extractor.extract(s.text())) {
        Long to = ids.get(ref.sectionNo());
        if (to == null) {
          unresolved++;
        } else if (!to.equals(from)) {
          repo.insertEdge(from, to, "REFERS_TO", "PARSER", false, null);
        }
      }
    }
    if (def.definitionsSection() != null) {
      Optional<ParsedSection> defs =
          sections.stream().filter(s -> s.sectionNo().equals(def.definitionsSection())).findFirst();
      Long defsId = ids.get(def.definitionsSection());
      if (defs.isPresent() && defsId != null) {
        List<Definition> terms = DefinitionExtractor.definitions(defs.get().text());
        boolean bn = def.authoritativeLang().equals("bn");
        for (Definition d : terms) {
          repo.insertTerm(
              defsId,
              def.code() + ":" + d.matching().replace(' ', '_'),
              bn ? d.term() : null,
              bn ? null : d.term());
        }
        for (ParsedSection s : sections) {
          Long from = ids.get(s.sectionNo());
          if (from == null || s.omitted() || s.sectionNo().equals(def.definitionsSection())) {
            continue;
          }
          if (!DefinitionExtractor.usedIn(terms, s.text()).isEmpty()) {
            repo.insertEdge(from, defsId, "USES_DEFINITION", "PARSER", false, null);
          }
        }
      }
    }
    return unresolved;
  }

  /** ALIGNS_WITH edges from the mapping file; unreviewed rows are stored with reviewed = false. */
  private void loadIloMapping(List<QualityReport.MissingLine> missing) {
    repo.clearMappingEdges();
    List<IloMappingLoader.Row> rows =
        IloMappingLoader.load(ingest.mappingFile(), repo::authoritativeText, repo.hasRelease());
    for (IloMappingLoader.Row row : rows) {
      Optional<Long> from = repo.currentSectionId(row.bdSectionKey());
      Optional<Long> to = repo.currentSectionId(row.iloSectionKey());
      if (from.isPresent() && to.isPresent()) {
        repo.insertEdge(
            from.get(), to.get(), "ALIGNS_WITH", "MAPPING", row.usable(), row.reviewedBy());
      } else {
        missing.add(
            new QualityReport.MissingLine(
                row.bdSectionKey() + " -> " + row.iloSectionKey(),
                "ILO_MAPPING_TARGET_MISSING (" + row.status() + ")"));
      }
    }
  }

  private List<QualityReport.IloLine> iloLines(PackManifest pack) {
    List<QualityReport.IloLine> out = new ArrayList<>();
    Path base = ingest.packFile().toAbsolutePath().getParent().getParent().getParent();
    for (IloConvention c : pack.iloConventions()) {
      String status;
      if (!c.reviewed()) {
        status = "SKIPPED_UNREVIEWED";
      } else if (!Files.isRegularFile(base.resolve(c.textFile()))) {
        status = "FILES_MISSING";
      } else {
        status = "FILES_PRESENT_NOT_PARSED";
      }
      out.add(new QualityReport.IloLine(c.code(), c.titleLabel(), status, null, c.normlexUrl()));
    }
    return out;
  }

  private QualityReport.Input reportInput(
      PackManifest pack,
      List<AcquiredAct> acquired,
      List<ActDefinition> selected,
      Map<String, ActWrite> writes,
      List<QualityReport.MissingLine> missing,
      List<QualityReport.IloLine> ilo,
      long unresolved,
      @Nullable String release,
      long seed,
      String gitSha,
      Instant at) {
    List<QualityReport.ActFacts> facts = new ArrayList<>();
    for (AcquiredAct a : acquired) {
      ParsedAct p = a.act();
      ActWrite w = writes.get(a.definition().code());
      List<String> problems = new ArrayList<>(p == null ? List.<String>of() : p.problems());
      if (w != null && !w.withdrawn().isEmpty()) {
        problems.add("WITHDRAWN_FROM_SOURCE: " + String.join(", ", w.withdrawn()));
      }
      facts.add(
          new QualityReport.ActFacts(
              a.definition().code(),
              p == null || p.title().isBlank() ? a.definition().titleEn() : p.title(),
              a.definition().jurisdiction(),
              a.definition().authoritativeLang(),
              p != null
                      && (p.title().contains("রহিত")
                          || p.title().toLowerCase(java.util.Locale.ROOT).contains("[repealed]"))
                  ? "REPEALED"
                  : "IN_FORCE",
              a.sourceKind().name(),
              a.parserVersion(),
              a.sourceUrls().isEmpty() ? "-" : a.sourceUrls().get(0),
              a.definition().required() && !a.definition().code().isBlank(),
              a.failure(),
              w == null ? 0 : w.duplicates().size(),
              p == null
                  ? List.of()
                  : p.amendingActs().entrySet().stream()
                      .map(e -> e.getKey() + " " + e.getValue())
                      .toList(),
              problems));
    }
    List<QualityReport.MissingLine> noted = new ArrayList<>(missing);
    return new QualityReport.Input(
        facts,
        repo.sectionFacts().stream()
            .filter(s -> facts.stream().anyMatch(f -> f.code().equals(s.actCode())))
            .toList(),
        repo.edgeCounts(),
        unresolved,
        ilo,
        noted,
        release,
        gitSha,
        seed,
        at);
  }

  private static @Nullable String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private static @Nullable Integer year(String text) {
    Matcher m = YEAR.matcher(BanglaNormalizer.toLatinDigits(text));
    Integer last = null;
    while (m.find()) {
      last = Integer.parseInt(m.group(1));
    }
    return last;
  }
}
