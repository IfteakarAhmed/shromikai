package bd.shromikai.eval;

import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.ClaimRecord;
import bd.shromikai.eval.Labels.GoldRisk;
import bd.shromikai.eval.Labels.LabelFile;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Computes every metric of the catalog from a run's results and assembles the report. Metrics that
 * cannot be measured with the data that exists are reported with the reason, never estimated.
 */
public final class ReportAssembler {

  private ReportAssembler() {}

  /** What the report is built from. */
  public record Inputs(
      EvalResults.Bundle results,
      EvalReport.Config config,
      List<LabelFile> labels,
      @Nullable JsonNode retrieval,
      List<String> extraLimitations,
      List<String> extraKnownFailures,
      List<String> errata) {

    /** For the smoke test: stand-in data, minimal configuration. */
    public static Inputs forSmoke(EvalResults.Bundle results, String runId, String dataNote) {
      Set<String> systems = new LinkedHashSet<>();
      Set<String> contracts = new LinkedHashSet<>();
      results
          .analyses()
          .forEach(
              a -> {
                systems.add(a.system());
                contracts.add(a.contractId());
              });
      EvalReport.Config config =
          new EvalReport.Config(
              runId,
              java.time.Instant.now().toString(),
              "smoke",
              true,
              "smoke",
              "contracts",
              "smoke",
              List.copyOf(systems),
              1,
              List.copyOf(contracts),
              Map.of(),
              Map.of(),
              "stand-in",
              "test runner",
              0,
              0,
              0,
              0,
              0,
              results.citations().size(),
              0,
              dataNote);
      return new Inputs(
          results,
          config,
          List.of(),
          null,
          List.of("Smoke run on scripted stand-ins."),
          List.of(),
          List.of());
    }
  }

  public static EvalReport assemble(Inputs in) {
    // Results are append-only: a case run again counts once, by its latest run.
    Map<String, AnalysisCase> latest = new LinkedHashMap<>();
    List<String> superseded = new ArrayList<>();
    for (AnalysisCase a : in.results().analyses()) {
      AnalysisCase earlier = latest.remove(a.contractId() + "|" + a.system() + "|" + a.side());
      if (earlier != null) {
        superseded.add(
            "superseded by a later run of the same case (not counted): "
                + earlier.contractId()
                + " / "
                + earlier.system()
                + ": "
                + earlier.outcome()
                + (earlier.failureCode() == null ? "" : " (" + earlier.failureCode() + ")"));
      }
      latest.put(a.contractId() + "|" + a.system() + "|" + a.side(), a);
    }
    List<AnalysisCase> all = List.copyOf(latest.values());
    List<AnalysisCase> ours = of(all, "shromikai");
    List<AnalysisCase> baseline = of(all, "baseline");
    Map<String, List<GoldRisk>> gold = new LinkedHashMap<>();
    in.labels().forEach(l -> gold.put(l.contractId(), l.risks()));
    String labelState =
        in.labels().stream().anyMatch(LabelFile::reviewed) ? "labels" : "draft labels (0 reviewed)";

    Map<String, MetricResult> m = new LinkedHashMap<>();
    m.put(
        "M-01",
        ours.isEmpty()
            ? MetricResult.notRun("M-01", "no ShromikAI analysis in this run")
            : Metrics.unverifiedExposure(ours));
    m.put(
        "M-02",
        MetricResult.notAvailable(
            "M-02",
            "needs a reviewer's judgement of every shown citation; no reviewer has judged them yet"));
    MetricResult raw = Metrics.objectiveFabrication(ours, "LLM");
    m.put(
        "M-03",
        raw.withExtra("shownCitationsFabricated", Metrics.shownFabrication(ours).display())
            .withNote(
                "raw risk-agent claims before verification; what is shown is in shownCitationsFabricated"));
    m.put("M-04", Metrics.fabricationCatch(in.results().citations(), all));
    m.put(
        "M-05",
        in.results().citations().isEmpty()
            ? MetricResult.notRun("M-05", "no adversarial citation was verified in this run")
            : Metrics.validAcceptance(in.results().citations()));
    if (gold.isEmpty() || ours.isEmpty()) {
      for (String id : List.of("M-06", "M-07", "M-08", "M-09", "M-10", "M-11")) {
        m.put(id, MetricResult.notAvailable(id, "no label files for the analysed contracts"));
      }
    } else {
      RiskMetrics r = RiskMetrics.of(ours, gold);
      m.put("M-06", r.recall().withNote(labelState));
      m.put("M-07", r.precisionLowerBound());
      m.put("M-08", r.threeRiskCoverage());
      m.put("M-09", r.nonObviousRecall());
      m.put("M-10", r.severityAgreement());
      m.put("M-11", r.correctAbstention());
    }
    m.put(
        "M-12",
        ours.isEmpty()
            ? MetricResult.notRun("M-12", "no ShromikAI analysis")
            : Metrics.abstentionRate(ours));
    m.put(
        "M-13",
        MetricResult.notAvailable(
            "M-13", "the contract set has no Bangla–English pair of the same contract"));
    m.put(
        "M-14",
        MetricResult.notRun(
            "M-14",
            "the employer side was not run: it doubles the cost and the live budget did not allow it"));
    m.put("M-15", retrieval(in.retrieval()));
    m.put("M-16", Metrics.piiEgress(all));
    m.put(
        "M-17",
        in.results().pii().stream().noneMatch(p -> "at-rest".equals(p.mode()))
            ? MetricResult.notRun("M-17", "no content-at-rest scan in this run")
            : Metrics.contentAtRest(in.results().pii()));
    m.put(
        "M-18",
        MetricResult.notRun(
            "M-18", "no injection set was built; three runs per case did not fit the live budget"));
    m.put("M-19", Metrics.latency(ours));
    m.put("M-20", Metrics.cost(ours));
    m.put("M-21", JudgeCalibration.decide(List.of(), List.of()).clarityMetric());
    m.put(
        "M-22",
        MetricResult.notAvailable(
            "M-22",
            "the pipeline does not keep the typed facts after a run (known gap: clauses[].facts is empty), so predicted facts cannot be compared"));
    m.put("M-23", segmentation(all, in.labels()));
    m.put(
        "M-24",
        MetricResult.notAvailable(
            "M-24", "no OCR set (phone photos and scans with ground truth) was built"));
    boolean local = in.results().pii().stream().anyMatch(p -> "local-model".equals(p.mode()));
    m.put(
        "M-25",
        local
            ? Metrics.piiRecall(in.results().pii(), "local-model")
            : MetricResult.notAvailable(
                "M-25", "the local PII model step was not measured (not measured)"));
    m.put(
        "M-26",
        MetricResult.notDone(
            "M-26", "LLM-as-judge cut: no human ratings to calibrate against and no Gemini judge"));
    m.put(
        "M-27",
        MetricResult.notAvailable(
            "M-27",
            "needs the legal reviewer's redline verdict files (eval/reviews/{runId}/); none exist yet"));
    m.put(
        "M-28",
        MetricResult.notAvailable(
            "M-28",
            "needs the legal reviewer's obligation verdict files and gold obligations; none exist yet"));

    Map<String, Map<String, MetricResult>> bySystem = new LinkedHashMap<>();
    bySystem.put("shromikai", m);
    if (!baseline.isEmpty()) {
      Map<String, MetricResult> b = new LinkedHashMap<>();
      b.put(
          "M-03",
          Metrics.objectiveFabrication(baseline, "BASELINE")
              .withNote("every baseline citation counts as shown (evaluation.md M-03)"));
      b.put("M-16", Metrics.piiEgress(baseline));
      b.put("M-20", Metrics.cost(baseline));
      long verified =
          claims(baseline, "BASELINE").stream().filter(c -> "VERIFIED".equals(c.status())).count();
      long total = claims(baseline, "BASELINE").size();
      b.put(
          "M-01",
          MetricResult.proportion(
              "M-01",
              total - verified,
              total,
              "baseline citations not VERIFIED by our verifier (the baseline itself shows all of them)"));
      bySystem.put("baseline", b);
    }

    return new EvalReport(
        in.config(),
        bySystem,
        ablations(ours, all),
        concat(superseded, failures(all)),
        limitations(in),
        knownFailures(in, m),
        in.errata());
  }

  private static List<String> concat(List<String> a, List<String> b) {
    List<String> out = new ArrayList<>(a);
    out.addAll(b);
    return out;
  }

  private static List<AnalysisCase> of(List<AnalysisCase> all, String system) {
    return all.stream().filter(a -> a.system().equals(system)).toList();
  }

  private static List<ClaimRecord> claims(List<AnalysisCase> cases, String origin) {
    List<ClaimRecord> out = new ArrayList<>();
    cases.forEach(
        c -> c.claims().stream().filter(x -> origin.equals(x.origin())).forEach(out::add));
    return out;
  }

  private static MetricResult retrieval(@Nullable JsonNode report) {
    if (report == null) {
      return MetricResult.notRun("M-15", "no retrieval report");
    }
    JsonNode best = null;
    for (JsonNode row : report.path("rows")) {
      if (row.path("method").asText().equals("HYBRID_GRAPH") && row.has("hitsAt5")) {
        best = row;
        break;
      }
    }
    if (best == null) {
      return MetricResult.notRun("M-15", "the retrieval report has no HYBRID_GRAPH row");
    }
    long questions = best.path("questions").asLong();
    long checked = report.path("questionsChecked").asLong(0);
    return MetricResult.proportion(
        "M-15",
        best.path("hitsAt5").asLong(),
        questions,
        questions
            + " retrieval questions ("
            + checked
            + " checked by a person; the rest draft); method "
            + best.path("method").asText(),
        Map.of(
            "recallAt8", best.path("recallAt8").asDouble(),
            "mrrAt10", best.path("mrrAt10").asDouble(),
            "corpusRelease", report.path("corpusRelease").asText(),
            "gitSha", report.path("gitSha").asText()));
  }

  private static MetricResult segmentation(List<AnalysisCase> all, List<LabelFile> labels) {
    Map<String, LabelFile> byContract = new LinkedHashMap<>();
    labels.forEach(l -> byContract.put(l.contractId(), l));
    int tp = 0;
    int fp = 0;
    int fn = 0;
    int correct = 0;
    int matched = 0;
    Set<String> seen = new LinkedHashSet<>();
    for (AnalysisCase a : all) {
      LabelFile l = byContract.get(a.contractId());
      if (l == null || a.clauses().isEmpty() || !seen.add(a.contractId())) {
        continue;
      }
      var s =
          Metrics.segmentation(
              a.clauses().stream()
                  .filter(c -> c.label() != null && !c.label().isBlank())
                  .map(c -> new Metrics.ClauseView(c.label(), c.type()))
                  .toList(),
              l.clauses().stream()
                  .filter(c -> c.label() != null)
                  .map(c -> new Metrics.ClauseView(c.label(), c.clauseType()))
                  .toList());
      tp += s.tp();
      fp += s.fp();
      fn += s.fn();
      correct += s.typeCorrect();
      matched += s.typeMatched();
    }
    if (seen.isEmpty()) {
      return MetricResult.notAvailable("M-23", "no labelled contract was segmented in this run");
    }
    double f1 = new Metrics.Segmentation(tp, fp, fn, correct, matched).f1();
    return MetricResult.number(
        "M-23",
        MetricResult.round(f1),
        seen.size(),
        seen.size()
            + " contracts; clause boundaries compared by printed clause label; draft labels",
        Map.of(
            "tp",
            tp,
            "fp",
            fp,
            "fn",
            fn,
            "typeAccuracy",
            matched == 0
                ? "n = 0"
                : String.format(
                    Locale.ROOT, "%.3f (%d of %d)", (double) correct / matched, correct, matched)));
  }

  private static List<EvalReport.Ablation> ablations(
      List<AnalysisCase> ours, List<AnalysisCase> all) {
    List<EvalReport.Ablation> out = new ArrayList<>();
    List<ClaimRecord> first = new ArrayList<>();
    ours.forEach(
        c ->
            c.claims().stream()
                .filter(x -> x.attempt() == 1 && "LLM".equals(x.origin()))
                .forEach(first::add));
    long notVerified = first.stream().filter(c -> !"VERIFIED".equals(c.status())).count();
    long fabricated =
        first.stream()
            .filter(c -> c.reasonCodes().stream().anyMatch(Metrics.OBJECTIVE_FABRICATION::contains))
            .count();
    Map<String, String> v = new LinkedHashMap<>();
    v.put(
        "M-01 if every first-attempt claim were shown",
        MetricResult.proportion("M-01", notVerified, first.size(), "").display());
    v.put(
        "objectively fabricated among them",
        MetricResult.proportion("M-03", fabricated, first.size(), "").display());
    out.add(
        new EvalReport.Ablation(
            "no-verifier",
            "derived from the raw risk-agent claims of the same ShromikAI runs, not a separate run",
            v));
    List<AnalysisCase> noRules = of(all, "no-rules");
    if (noRules.isEmpty()) {
      out.add(new EvalReport.Ablation("no-rules", "not run", Map.of()));
    } else {
      Map<String, String> nr = new LinkedHashMap<>();
      nr.put("M-01", Metrics.unverifiedExposure(noRules).display());
      nr.put("M-12", Metrics.abstentionRate(noRules).display());
      nr.put("findings", String.valueOf(noRules.stream().mapToInt(a -> a.findings().size()).sum()));
      out.add(new EvalReport.Ablation("no-rules", noRules.size() + " run(s), rule engine off", nr));
    }
    out.add(
        new EvalReport.Ablation(
            "verifier-same-family",
            "not run: there is no cross-family verifier (Gemini dropped, ADR-0018); every verification in this run is SAME_FAMILY",
            Map.of()));
    return out;
  }

  private static List<String> failures(List<AnalysisCase> all) {
    List<String> out = new ArrayList<>();
    for (AnalysisCase a : all) {
      if (!"ANALYSED".equals(a.outcome())) {
        out.add(
            a.contractId()
                + " / "
                + a.system()
                + ": "
                + a.outcome()
                + " ("
                + a.failureCode()
                + ")");
        continue;
      }
      long uncertain =
          a.findings().stream().filter(f -> "UNCERTAIN".equals(f.verification())).count();
      Map<String, Long> reasons = new TreeMap<>();
      a.claims().stream()
          .filter(c -> !"VERIFIED".equals(c.status()))
          .forEach(c -> c.reasonCodes().forEach(r -> reasons.merge(r, 1L, Long::sum)));
      out.add(
          a.contractId()
              + " / "
              + a.system()
              + ": "
              + a.findings().size()
              + " findings, "
              + uncertain
              + " UNCERTAIN; reason codes of claims not verified: "
              + reasons
              + (a.warnings().isEmpty() ? "" : "; warnings: " + a.warnings()));
    }
    return out;
  }

  private static List<String> limitations(Inputs in) {
    List<String> out = new ArrayList<>();
    out.add(
        "Small sample: "
            + in.config().contracts().size()
            + " synthetic contracts, one side (worker), one run per system. Every result on fewer than 10 cases is indicative; no significance claim is made and no difference interval is computed.");
    out.add(
        "Synthetic data only: contracts were written by the team with invented names and numbers; no real contract and no public template is in the set.");
    out.add(
        "Dev split only: all contracts are the P5 dev split and the prompts were developed against syn-en-02; there is no frozen test split yet.");
    out.add(
        "Same-family verifier: every support check is Claude Haiku judging Claude Sonnet's claims (SAME_FAMILY); no independent verifier was run (ADR-0018).");
    out.add(
        "Unreviewed, circular labels: the gold labels were drafted by Claude, the same model family as the system under test, and no person has reviewed them (0 reviewed labels). Risk recall, precision, severity agreement and abstention are measured against these drafts and may share the system's blind spots.");
    out.add(
        "The adversarial claims (valid, contradicted, unrelated) were drafted by Claude from the section text and are unreviewed; the nonexistent, non-current and misquote cases are generated mechanically from the corpus.");
    out.add(
        "M-04 mixes independent evidence (the adversarial set) with a consistency check (baseline and raw-agent citations that fail the same existence and quote checks that define them as fabricated); the breakdown is in the M-04 details.");
    out.add(
        "Retrieval (M-15) uses draft questions written from the statute text by the team; none is checked yet.");
    out.add(
        "Latency was measured on a development Mac with the local PII model in Docker without a GPU, which dominates the REDACT stage; the p95 is over very few live analyses.");
    out.addAll(in.extraLimitations());
    return out;
  }

  private static List<String> knownFailures(Inputs in, Map<String, MetricResult> m) {
    List<String> out = new ArrayList<>(in.extraKnownFailures());
    Object missed = m.get("M-04").extra().get("missedCases");
    if (missed instanceof List<?> list && !list.isEmpty()) {
      out.add("Fabricated citations that ended VERIFIED: " + list);
    }
    MetricResult m01 = m.get("M-01");
    if (m01.count() != null && m01.count() > 0) {
      out.add("Unverified citations were shown: " + m01.display());
    }
    return out;
  }
}
