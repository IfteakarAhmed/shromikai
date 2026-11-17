package bd.shromikai.eval;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import bd.shromikai.analysis.SampleContract;
import bd.shromikai.analysis.Side;
import bd.shromikai.analysis.live.LiveBase;
import bd.shromikai.eval.AdversarialGenerator.ClaimDraft;
import bd.shromikai.eval.AdversarialGenerator.SectionRow;
import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.CitationCase;
import bd.shromikai.eval.EvalResults.PiiCase;
import bd.shromikai.eval.harness.AtRestScanner;
import bd.shromikai.eval.harness.CloudCapture;
import bd.shromikai.eval.harness.EvalHarness;
import bd.shromikai.eval.harness.EvalWrapping;
import bd.shromikai.ingest.IntakeService;
import bd.shromikai.law.LawReadService;
import bd.shromikai.privacy.PlaceholderMap;
import bd.shromikai.privacy.PrivacyShield;
import bd.shromikai.privacy.RedactedParagraph;
import bd.shromikai.privacy.RoleWordAllowlist;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.live.LiveEnv;
import bd.shromikai.testsupport.live.LiveSpendLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The evaluation runner, {@code ./gradlew evaluate -Pstep=...} (FR-80, evaluation.md §5). It runs
 * against the development database (the real corpus) and the real local models; a step that may
 * call a cloud model needs {@code -PliveOk=eval} and stays under the spend caps of {@link
 * EvalWrapping}. Steps, run in this order:
 *
 * <ul>
 *   <li>{@code local}: local PII redaction of every contract (M-25), the adversarial set from the
 *       corpus; no cloud call;
 *   <li>{@code citations}: the adversarial citations through the real verifier (M-04, M-05);
 *   <li>{@code contracts}: {@code -Pcontracts=a,b -Psystems=shromikai,baseline}, one at a time;
 *   <li>{@code report}: the report from everything the run folder holds.
 * </ul>
 *
 * Each step appends content-free results to {@code eval/reports/runs/{date}-{runId}/} and scans the
 * database, its own logs and new temp files for canaries (M-17). Without {@code -Pstep} the class
 * is skipped, so {@code evalRetrieval} (same tag) does not run it.
 */
@Tag("eval")
@Requirement("FR-80")
@SpringBootTest
@ActiveProfiles("dev")
@Import({EvalWrapping.class, EvalHarness.class})
class EvaluateRunnerIT {

  private static final String STEP = System.getProperty("shromikai.eval.step", "");
  private static final String RUN_ID = System.getProperty("shromikai.eval.runId", "");
  private static final Path REPO = Path.of("..");
  private static final Path REPORTS = REPO.resolve("eval/reports");
  private static final Path LABELS = REPO.resolve("eval/labels");
  private static final Path CITATIONS = REPO.resolve("eval/citations");
  private static final long SEED = 42;

  /** Paths the runner itself writes; any other change makes the tree dirty. */
  private static final List<String> RUNNER_OUTPUTS =
      List.of(
          "eval/reports/",
          "eval/citations/adversarial.jsonl",
          "eval/labels/export/",
          "backend/src/test/resources/fixtures/llm-eval/");

  private static final ObjectMapper JSON =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  @Autowired EvalHarness harness;
  @Autowired IntakeService intake;
  @Autowired PrivacyShield shield;
  @Autowired LawReadService law;
  @Autowired JdbcClient jdbc;
  @Autowired SampleContract sample;
  @Autowired RoleWordAllowlist roleWords;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    LiveBase.live(registry);
    // The product's daily budget counts replayed recordings at their original price (known gap);
    // the spend caps of EvalWrapping are the real limit here.
    registry.add("shromikai.budget.daily-usd", () -> "100000");
  }

  @BeforeAll
  static void selected() {
    assumeTrue(!STEP.isBlank(), "evaluation step not selected (-Pstep=...)");
    if (RUN_ID.isBlank()) {
      throw new IllegalStateException("-PrunId is required");
    }
  }

  private static Path runDir() {
    return REPORTS.resolve("runs").resolve(LocalDate.now(java.time.ZoneOffset.UTC) + "-" + RUN_ID);
  }

  private static List<String> list(String property, String fallback) {
    String v = System.getProperty(property, "");
    return Arrays.stream((v.isBlank() ? fallback : v).split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  @Test
  void evaluate() throws Exception {
    Files.createDirectories(runDir());
    checkCleanTree();
    checkCorpusRelease();
    AtRestScanner atRest = new AtRestScanner();
    List<String> documents = new ArrayList<>();
    switch (STEP) {
      case "local" -> local(documents);
      case "citations" -> citations();
      case "contracts" -> contracts(documents);
      case "report" -> report();
      default -> throw new IllegalArgumentException("Unknown step " + STEP);
    }
    if (!STEP.equals("report")) {
      List<String> secrets = new ArrayList<>();
      for (String id : SyntheticContracts.IDS) {
        secrets.add(SyntheticContracts.marker(id));
        secrets.addAll(SyntheticContracts.allValues(id));
        documents.add(SyntheticContracts.text(id));
      }
      var scan = atRest.scan(jdbc, documents, secrets);
      EvalResults.append(
          runDir(),
          List.of(),
          List.of(),
          List.of(
              new PiiCase(
                  "scan:" + STEP + ":" + Instant.now(),
                  "at-rest",
                  0,
                  Map.of(),
                  scan.offenders().size(),
                  scan.scanned())));
      note(
          "at-rest scan after step "
              + STEP
              + ": "
              + scan.offenders().size()
              + " hit(s) "
              + scan.offenders());
    }
  }

  // ------------------------------------------------------------------ steps

  /** Local only: PII redaction with the real local model, and the adversarial set. */
  private void local(List<String> documents) throws IOException {
    EvalWrapping.Run.liveAllowed = false;
    Set<String> standIn = new LinkedHashSet<>();
    List<PiiCase> cases = new ArrayList<>();
    List<String> only = list("shromikai.eval.contracts", "");
    for (String id : SyntheticContracts.IDS) {
      if (!only.isEmpty() && !only.contains(id)) {
        continue;
      }
      String text = SyntheticContracts.text(id);
      documents.add(text);
      EvalWrapping.Run.currentContract = id;
      List<String> paragraphs =
          intake.fromText(text).paragraphs().stream().map(p -> p.text().reveal()).toList();
      long t0 = System.currentTimeMillis();
      PlaceholderMap map = new PlaceholderMap("eval-" + id);
      List<RedactedParagraph> redacted;
      String mode = "local-model";
      try {
        redacted = shield.redact(paragraphs, map);
      } catch (RuntimeException e) {
        note("local PII step failed for " + id + ": " + e.getClass().getSimpleName());
        redacted = null;
      }
      long ms = System.currentTimeMillis() - t0;
      if (redacted == null || ms > 15 * 60_000) {
        standIn.add(id);
        mode = "local-model-failed";
      }
      Map<String, List<Long>> byType = new TreeMap<>();
      if (redacted != null) {
        String all = String.join("\n", redacted.stream().map(r -> r.text().reveal()).toList());
        for (var e : SyntheticContracts.entities(id)) {
          List<Long> rt = byType.computeIfAbsent(e.type(), k -> new ArrayList<>(List.of(0L, 0L)));
          rt.set(1, rt.get(1) + 1);
          if (!CloudCapture.containsAny(all, List.of(e.value()))) {
            rt.set(0, rt.get(0) + 1);
          }
        }
      }
      String roleNote = redacted == null ? "n/a" : roleWordsKept(id, paragraphs, redacted);
      map.clear();
      cases.add(new PiiCase(id, mode, ms, byType, 0, List.of()));
      note(
          String.format(
              Locale.ROOT,
              "local PII %s: %s in %.1f s, %s; role words kept in the text: %s",
              id,
              mode,
              ms / 1000.0,
              byType,
              roleNote));
    }
    EvalResults.append(runDir(), List.of(), List.of(), cases);
    Files.writeString(
        runDir().resolve("pii-mode.json"), JSON.writeValueAsString(Map.of("standIn", standIn)));
    writeAdversarialSet();
  }

  /**
   * Generic role words (ADR-0025) that occur as whole tokens of the original and how many of those
   * occurrences are still in the redacted text. The counts and words come from the allowlist, never
   * from a personal value. For a paragraph that lost a role word the line also quotes the redacted
   * paragraph (at most 300 characters): that is contract text, so this writer is for the team's
   * synthetic contracts only and refuses any other contract ID (R-02).
   */
  private String roleWordsKept(
      String contractId, List<String> original, List<RedactedParagraph> redacted) {
    if (!SyntheticContracts.IDS.contains(contractId)) {
      throw new IllegalStateException(
          "The role-word check writes redacted paragraphs to notes.md; it is for synthetic contract"
              + " IDs only");
    }
    java.util.Map<String, long[]> counts = new TreeMap<>();
    for (String paragraph : original) {
      for (String token : paragraph.split("\\s+")) {
        String word = token.replaceAll("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$", "");
        if (roleWords.isRoleWord(word)) {
          counts.computeIfAbsent(word, w -> new long[2])[0]++;
        }
      }
    }
    for (RedactedParagraph paragraph : redacted) {
      for (String token : paragraph.text().reveal().split("\\s+")) {
        String word = token.replaceAll("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$", "");
        if (roleWords.isRoleWord(word)) {
          counts.computeIfAbsent(word, w -> new long[2])[1]++;
        }
      }
    }
    StringBuilder lost = new StringBuilder();
    for (int i = 0; i < original.size() && i < redacted.size(); i++) {
      if (roleTokens(original.get(i)) > roleTokens(redacted.get(i).text().reveal())) {
        String shown = redacted.get(i).text().reveal();
        lost.append(" | role word lost in redacted paragraph ")
            .append(i)
            .append(": ")
            .append(shown.length() > 300 ? shown.substring(0, 300) : shown);
      }
    }
    long total = counts.values().stream().mapToLong(c -> c[0]).sum();
    long kept = counts.values().stream().mapToLong(c -> Math.min(c[0], c[1])).sum();
    StringBuilder words = new StringBuilder();
    counts.forEach(
        (w, c) -> words.append(w).append('=').append(c[1]).append('/').append(c[0]).append(' '));
    return kept + " of " + total + " (" + words.toString().strip() + ")" + lost;
  }

  private long roleTokens(String text) {
    return java.util.Arrays.stream(text.split("\\s+"))
        .map(t -> t.replaceAll("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$", ""))
        .filter(roleWords::isRoleWord)
        .count();
  }

  private void writeAdversarialSet() throws IOException {
    List<SectionRow> rows =
        jdbc
            .sql(
                """
                select s.section_key, a.code, s.section_no, s.is_current,
                       coalesce(case when a.authoritative_lang = 'bn' then s.text_bn else s.text_en end, '') as text,
                       a.authoritative_lang
                from law_section s join law_act a on a.id = s.act_id
                where a.jurisdiction = 'BD'
                order by a.code, s.id
                """)
            .query(
                (rs, i) ->
                    new SectionRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getBoolean(4),
                        rs.getString(5),
                        rs.getString(6)))
            .list()
            .stream()
            .filter(r -> !r.text().isBlank())
            .toList();
    JsonNode draftsYaml =
        new YAMLMapper().readTree(CITATIONS.resolve("claims-draft.yaml").toFile());
    List<ClaimDraft> drafts = new ArrayList<>();
    for (JsonNode d : draftsYaml.path("drafts")) {
      drafts.add(
          new ClaimDraft(
              d.path("sectionKey").asText(),
              d.path("quote").asText(),
              d.path("validClaim").asText(),
              d.hasNonNull("contradictedClaim") ? d.path("contradictedClaim").asText() : null,
              d.path("topic").asText()));
    }
    List<AdversarialCase> cases = AdversarialGenerator.generate(rows, drafts, SEED);
    List<String> missing =
        drafts.stream()
            .filter(
                d ->
                    cases.stream()
                        .noneMatch(
                            c ->
                                c.cls().equals("VALID_CONTROL")
                                    && c.sectionKey().equals(d.sectionKey())))
            .map(ClaimDraft::sectionKey)
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "These drafted quotes are not in the current section text: " + missing);
    }
    ObjectMapper line = new ObjectMapper();
    StringBuilder out = new StringBuilder();
    for (AdversarialCase c : cases) {
      out.append(line.writeValueAsString(c)).append('\n');
    }
    Path file = CITATIONS.resolve("adversarial.jsonl");
    if (Files.isRegularFile(file) && !Files.readString(file).equals(out.toString())) {
      throw new IllegalStateException(
          "adversarial.jsonl exists and differs from what seed "
              + SEED
              + " gives; not overwritten");
    }
    Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    Map<String, Long> byClass = new TreeMap<>();
    cases.forEach(c -> byClass.merge(c.cls(), 1L, Long::sum));
    note("adversarial set: " + cases.size() + " cases " + byClass);
  }

  private void live(String purpose, String caseId, long capMicros, int maxCalls) {
    if (!LiveEnv.optedIn("eval")) {
      throw new IllegalStateException("This step may call cloud models: run with -PliveOk=eval");
    }
    CloudCapture.activate();
    EvalWrapping.Run.purpose = purpose;
    EvalWrapping.Run.caseId = caseId;
    EvalWrapping.Run.stepStartMicros = new LiveSpendLedger().spentOn(LiveSpendLedger.EVAL_PREFIX);
    EvalWrapping.Run.stepCapMicros = capMicros;
    EvalWrapping.Run.maxLiveCalls = maxCalls;
    EvalWrapping.Run.liveAllowed = true;
  }

  private static long usd(String property, double fallback) {
    String v = System.getProperty(property, "");
    return Math.round((v.isBlank() ? fallback : Double.parseDouble(v)) * 1_000_000);
  }

  private void citations() throws IOException {
    long before = new LiveSpendLedger().spentOn("eval:citations");
    live("eval:citations", "eval-citations", usd("shromikai.eval.stepCapUsd", 0.05), 60);
    ObjectMapper mapper = new ObjectMapper();
    List<CitationCase> results = new ArrayList<>();
    for (String l : Files.readAllLines(CITATIONS.resolve("adversarial.jsonl"))) {
      if (l.isBlank()) {
        continue;
      }
      AdversarialCase c = mapper.readValue(l, AdversarialCase.class);
      results.add(harness.verify(c));
    }
    EvalResults.append(runDir(), List.of(), results, List.of());
    long spent = new LiveSpendLedger().spentOn("eval:citations") - before;
    Map<String, Long> byStatus = new TreeMap<>();
    results.forEach(r -> byStatus.merge(r.cls() + "->" + r.actualStatus(), 1L, Long::sum));
    note(
        String.format(
            Locale.ROOT,
            "citations: %d cases %s; live spend US$%.4f; eval total US$%.4f",
            results.size(),
            byStatus,
            spent / 1e6,
            new LiveSpendLedger().spentOn(LiveSpendLedger.EVAL_PREFIX) / 1e6));
  }

  private void contracts(List<String> documents) throws IOException {
    List<String> systems = list("shromikai.eval.systems", "shromikai,baseline");
    Set<String> standIn = new LinkedHashSet<>();
    Path modes = runDir().resolve("pii-mode.json");
    if (Files.isRegularFile(modes)) {
      new ObjectMapper()
          .readTree(modes.toFile())
          .path("standIn")
          .forEach(n -> standIn.add(n.asText()));
    }
    for (String contract : list("shromikai.eval.contracts", "")) {
      LiveSpendLedger ledger = new LiveSpendLedger();
      long before = ledger.spentOn("eval:" + contract);
      boolean demo = contract.equals(SampleContract.ID);
      // syn-en-02 replays the demo recordings, which were made from the canary-free sample text
      // with the ground-truth entity list for the local step; other contracts use the real model.
      Set<String> pii = new LinkedHashSet<>(standIn);
      if (demo) {
        pii.add(contract);
      }
      EvalWrapping.Run.refusals.clear();
      EvalWrapping.Run.piiStandIn = pii;
      EvalWrapping.Run.currentContract = contract;
      EvalWrapping.Run.demoCase = demo ? SampleContract.ID + "-worker" : null;
      live(
          "eval:" + contract,
          "eval-" + contract + "-worker",
          usd("shromikai.eval.stepCapUsd", 0.80),
          // A full run makes about 60 to 80 calls (12 rewrites, 20 support checks, counter-offers);
          // 60 stopped the first syn-en-01 run at its last call. The spend caps are the real limit.
          200);
      String text = demo ? sample.text() : SyntheticContracts.text(contract);
      documents.add(text);
      harness.piiMode(pii.contains(contract) ? "ground-truth-stand-in" : "local-model");
      List<AnalysisCase> cases =
          harness.run(contract, text, Side.WORKER, systems, SyntheticContracts.allValues(contract));
      EvalResults.append(runDir(), cases, List.of(), List.of());
      for (String refusal : EvalWrapping.Run.refusals) {
        note("spend guard, " + contract + ": " + refusal);
      }
      long spent = new LiveSpendLedger().spentOn("eval:" + contract) - before;
      StringBuilder summary = new StringBuilder();
      for (AnalysisCase c : cases) {
        summary.append(
            String.format(
                Locale.ROOT,
                " [%s %s: %d findings, %d shown, %d live + %d replayed calls, priced US$%.4f, %d ms]",
                c.system(),
                c.outcome() + (c.failureCode() == null ? "" : "(" + c.failureCode() + ")"),
                c.findings().size(),
                c.shown().size(),
                c.liveCalls(),
                c.replayedCalls(),
                c.costMicros() / 1e6,
                c.latencyMs()));
      }
      note(
          String.format(
              Locale.ROOT,
              "contract %s: live spend US$%.4f; eval total US$%.4f; ledger total US$%.4f;%s",
              contract,
              spent / 1e6,
              new LiveSpendLedger().spentOn(LiveSpendLedger.EVAL_PREFIX) / 1e6,
              new LiveSpendLedger().spentMicros() / 1e6,
              summary));
    }
    EvalWrapping.Run.liveAllowed = false;
  }

  private void report() throws Exception {
    EvalResults.Bundle results = EvalResults.read(runDir());
    List<Labels.LabelFile> labels = Labels.loadAll(LABELS);
    Files.createDirectories(LABELS.resolve("export"));
    Files.writeString(
        LABELS.resolve("export/labels.csv"), Labels.csv(labels), StandardCharsets.UTF_8);
    Labels.Summary summary = Labels.summarise(labels);
    JsonNode retrieval =
        Files.isRegularFile(REPORTS.resolve("retrieval.json"))
            ? new ObjectMapper().readTree(REPORTS.resolve("retrieval.json").toFile())
            : null;
    Map<String, String> models = new TreeMap<>();
    Map<String, Integer> prompts = new TreeMap<>();
    Set<String> contracts = new LinkedHashSet<>();
    Set<String> systems = new LinkedHashSet<>();
    for (AnalysisCase c : results.analyses()) {
      c.models().forEach(models::putIfAbsent);
      c.promptVersions().forEach(prompts::putIfAbsent);
      contracts.add(c.contractId());
      systems.add(c.system());
    }
    List<String> adversarial = Files.readAllLines(CITATIONS.resolve("adversarial.jsonl"));
    long reviewedAdversarial =
        adversarial.stream()
            .filter(l -> !l.isBlank() && !l.contains("\"reviewedBy\":null"))
            .count();
    LiveSpendLedger ledger = new LiveSpendLedger();
    Runtime rt = Runtime.getRuntime();
    EvalReport.Config config =
        new EvalReport.Config(
            RUN_ID,
            Instant.now().toString(),
            git("rev-parse", "--short", "HEAD").strip(),
            dirtyPaths().isEmpty(),
            String.valueOf(law.corpusRelease()),
            "contracts, citations, pii",
            "dev (no frozen test split exists)",
            List.copyOf(systems),
            1,
            List.copyOf(contracts),
            models,
            prompts,
            "SAME_FAMILY: support checks by Claude Haiku (verifier-fallback); no Gemini, no cross-family verifier",
            System.getProperty("os.name")
                + " "
                + System.getProperty("os.arch")
                + ", "
                + rt.availableProcessors()
                + " CPUs; local qwen2.5:7b-instruct and bge-m3 on Ollama in Docker without a GPU",
            ledger.spentOn(LiveSpendLedger.EVAL_PREFIX),
            ledger.spentMicros(),
            SEED,
            summary.files(),
            summary.reviewed(),
            (int) adversarial.stream().filter(l -> !l.isBlank()).count(),
            (int) reviewedAdversarial,
            contracts.size()
                + " synthetic contracts written by the team (invented names and numbers), worker side");
    List<String> limitations = new ArrayList<>();
    List<String> knownFailures =
        new ArrayList<>(
            List.of(
                "The transfer finding of the demo contract cites BLA-2006:3, which does not clearly"
                    + " support the claim; the same-family verifier passed it (docs/known-gaps.md,"
                    + " cross-check of 17 November)."));
    List<String> blocked =
        EvalResults.latest(results.analyses()).stream()
            .filter(a -> "privacy-guard-blocked".equals(a.failureCode()))
            .map(a -> a.contractId() + " (" + a.system() + ")")
            .toList();
    if (!blocked.isEmpty()) {
      // These runs happened before the role-word allowlist of ADR-0025 and were not repeated: the
      // report's failures are the pre-fix outcome, not what the guard does after it.
      knownFailures.add(
          "The privacy-guard-blocked failures listed here ("
              + String.join(", ", blocked)
              + ") predate the role-word allowlist of ADR-0025 and were not rerun; after it,"
              + " syn-bn-01 (ShromikAI) passed the guard in a live rerun, the syn-bn-01 baseline"
              + " was not rerun, and syn-bn-03 stays blocked on the name-part path"
              + " (BanglaRoleWordsFailClosedTest; local run p10c; no live rerun).");
    }
    if (results.pii().stream().anyMatch(p -> "local-model-failed".equals(p.mode()))) {
      limitations.add(
          "The local PII model failed or took over 15 minutes on at least one contract; that contract's cloud runs used the ground-truth entity list (see M-25 details).");
    }
    limitations.add(
        "syn-en-02 replays the demo recordings of 16 November, made from the canary-free sample text with the ground-truth entity list standing in for the local PII model; its M-16 scan covers its PII values, not the content marker.");
    EvalReport report =
        ReportAssembler.assemble(
            new ReportAssembler.Inputs(
                results, config, labels, retrieval, limitations, knownFailures, List.of()));
    ReportGenerator.writeRun(report, REPORTS);
    Path runCopy = REPORTS.resolve("runs").resolve(ReportGenerator.runFolder(config));
    if (!runCopy.equals(runDir().normalize())
        && !runCopy.normalize().equals(runDir().normalize())) {
      note("report run folder " + runCopy + " differs from results folder " + runDir());
    }
    note("report written: " + REPORTS.resolve("report.md"));
  }

  // ------------------------------------------------------------------ checks and notes

  private static String git(String... args) throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>(List.of("git", "-C", REPO.toAbsolutePath().toString()));
    cmd.addAll(List.of(args));
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    p.waitFor();
    return out;
  }

  private static List<String> dirtyPaths() throws IOException, InterruptedException {
    List<String> dirty = new ArrayList<>();
    for (String line : git("status", "--porcelain", "--untracked-files=all").split("\n")) {
      if (line.length() < 4) {
        continue;
      }
      String path = line.substring(3).strip();
      if (RUNNER_OUTPUTS.stream().noneMatch(path::startsWith)) {
        dirty.add(path);
      }
    }
    return dirty;
  }

  /** evaluation.md §5: the runner refuses a dirty tree (its own outputs aside). */
  private static void checkCleanTree() throws IOException, InterruptedException {
    List<String> dirty = dirtyPaths();
    if (!dirty.isEmpty() && !Boolean.getBoolean("shromikai.eval.allowDirty")) {
      throw new IllegalStateException("The working tree is not clean: " + dirty);
    }
  }

  /** The labels must name the corpus release the verifier reads. */
  private void checkCorpusRelease() {
    String release = law.corpusRelease();
    for (Labels.LabelFile l : Labels.loadAll(LABELS)) {
      if (!l.corpusRelease().equals(release)) {
        throw new IllegalStateException(
            l.contractId()
                + " labels name corpus release "
                + l.corpusRelease()
                + ", the database has "
                + release);
      }
    }
  }

  private static void note(String line) throws IOException {
    Files.writeString(
        runDir().resolve("notes.md"),
        "- " + Instant.now() + " " + line + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }
}
