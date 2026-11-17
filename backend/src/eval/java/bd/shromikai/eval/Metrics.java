package bd.shromikai.eval;

import bd.shromikai.eval.EvalResults.AnalysisCase;
import bd.shromikai.eval.EvalResults.CitationCase;
import bd.shromikai.eval.EvalResults.ClaimRecord;
import bd.shromikai.eval.EvalResults.PiiCase;
import bd.shromikai.eval.EvalResults.PredictedFinding;
import bd.shromikai.eval.EvalResults.ShownCitation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The formulas of evaluation.md §6 over content-free results. */
public final class Metrics {

  /**
   * Reason codes that prove a citation fabricated without any judgement: the section does not exist
   * (or belongs to another act), or the quote is not in the section.
   */
  public static final Set<String> OBJECTIVE_FABRICATION =
      Set.of("SECTION_NOT_FOUND", "ACT_MISMATCH", "QUOTE_NOT_FOUND", "QUOTE_BELOW_THRESHOLD");

  private Metrics() {}

  /** M-01: shown citations whose status is not VERIFIED, deduplicated by citation ID. */
  public static MetricResult unverifiedExposure(List<AnalysisCase> cases) {
    Map<String, ShownCitation> unique = new LinkedHashMap<>();
    for (AnalysisCase c : cases) {
      c.shown().forEach(s -> unique.putIfAbsent(c.contractId() + "/" + s.citationId(), s));
    }
    long bad = unique.values().stream().filter(s -> !"VERIFIED".equals(s.status())).count();
    return MetricResult.proportion(
        "M-01", bad, unique.size(), cases.size() + " analyses; every shown citation");
  }

  private static boolean objectivelyFabricated(ClaimRecord c) {
    return c.reasonCodes().stream().anyMatch(OBJECTIVE_FABRICATION::contains);
  }

  /** M-03 (objective part): claims of one origin that are provably fabricated. */
  public static MetricResult objectiveFabrication(List<AnalysisCase> cases, String origin) {
    long n = 0;
    long fabricated = 0;
    Map<String, Long> byReason = new TreeMap<>();
    for (AnalysisCase c : cases) {
      for (ClaimRecord claim : c.claims()) {
        if (!origin.equals(claim.origin())) {
          continue;
        }
        n++;
        if (objectivelyFabricated(claim)) {
          fabricated++;
        }
        claim.reasonCodes().forEach(r -> byReason.merge(r, 1L, Long::sum));
      }
    }
    return MetricResult.proportion(
        "M-03",
        fabricated,
        n,
        origin + " claims; fabricated = section missing or quote not in it (no reviewer)",
        Map.of("reasonCodes", byReason));
  }

  /** M-03 for what ShromikAI shows: shown citations that are objectively fabricated. */
  public static MetricResult shownFabrication(List<AnalysisCase> cases) {
    long n = cases.stream().mapToLong(c -> c.shown().size()).sum();
    return MetricResult.proportion(
        "M-03",
        0,
        n,
        "shown citations; every one passed the existence and quote checks by construction");
  }

  /**
   * M-04: fabricated citations that end REJECTED or UNCERTAIN. Fabricated means an adversarial case
   * of an invalid class, or a baseline or raw-agent claim with an objective fabrication reason.
   */
  public static MetricResult fabricationCatch(
      List<CitationCase> adversarial, List<AnalysisCase> cases) {
    long n = 0;
    long caught = 0;
    List<String> missed = new ArrayList<>();
    long[] adv = new long[2];
    long[] base = new long[2];
    long[] raw = new long[2];
    for (CitationCase c : adversarial) {
      if (!c.fabricated()) {
        continue;
      }
      n++;
      adv[1]++;
      if (!"VERIFIED".equals(c.actualStatus())) {
        caught++;
        adv[0]++;
      } else {
        missed.add(c.id());
      }
    }
    for (AnalysisCase a : cases) {
      for (ClaimRecord claim : a.claims()) {
        if (!objectivelyFabricated(claim)) {
          continue;
        }
        long[] bucket = "BASELINE".equals(claim.origin()) ? base : raw;
        n++;
        bucket[1]++;
        if (!"VERIFIED".equals(claim.status())) {
          caught++;
          bucket[0]++;
        } else {
          missed.add(
              a.contractId()
                  + "/"
                  + a.system()
                  + "/"
                  + claim.findingId()
                  + "/"
                  + claim.sectionKey());
        }
      }
    }
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("misses", n - caught);
    extra.put("missedCases", missed);
    extra.put("adversarialCaught", adv[0]);
    extra.put("adversarialFabricated", adv[1]);
    extra.put("baselineCaught", base[0]);
    extra.put("baselineFabricated", base[1]);
    extra.put("rawAgentCaught", raw[0]);
    extra.put("rawAgentFabricated", raw[1]);
    return MetricResult.proportion(
        "M-04",
        caught,
        n,
        "adversarial set (labels by construction) plus objectively fabricated baseline and raw-agent"
            + " claims (caught by the same checks that define them: a consistency check, not"
            + " independent evidence)",
        extra);
  }

  /** M-05: VALID_CONTROL cases that end VERIFIED. */
  public static MetricResult validAcceptance(List<CitationCase> cases) {
    long n = cases.stream().filter(c -> !c.fabricated()).count();
    long ok =
        cases.stream().filter(c -> !c.fabricated() && "VERIFIED".equals(c.actualStatus())).count();
    return MetricResult.proportion("M-05", ok, n, "VALID_CONTROL cases of the adversarial set");
  }

  /** M-12: UNCERTAIN findings among findings that are not POLICY_ONLY. */
  public static MetricResult abstentionRate(List<AnalysisCase> cases) {
    long n = 0;
    long uncertain = 0;
    for (AnalysisCase c : cases) {
      for (PredictedFinding f : c.findings()) {
        if ("POLICY_ONLY".equals(f.verification())) {
          continue;
        }
        n++;
        if ("UNCERTAIN".equals(f.verification())) {
          uncertain++;
        }
      }
    }
    return MetricResult.proportion("M-12", uncertain, n, cases.size() + " analyses");
  }

  /** M-19: p50 and p95 of job latency over live (not replayed) finished analyses. */
  public static MetricResult latency(List<AnalysisCase> cases) {
    List<Double> seconds = new ArrayList<>();
    List<Double> withoutRedact = new ArrayList<>();
    for (AnalysisCase c : cases) {
      if (c.recorded() || !"ANALYSED".equals(c.outcome())) {
        continue;
      }
      seconds.add(c.latencyMs() / 1000.0);
      withoutRedact.add((c.latencyMs() - c.stageMs().getOrDefault("REDACT", 0L)) / 1000.0);
    }
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("p50Seconds", Stats.nearestRank(seconds, 0.5));
    extra.put("p95Seconds", Stats.nearestRank(seconds, 0.95));
    extra.put("p50SecondsWithoutRedact", Stats.nearestRank(withoutRedact, 0.5));
    extra.put("p95SecondsWithoutRedact", Stats.nearestRank(withoutRedact, 0.95));
    return MetricResult.number(
        "M-19",
        Stats.nearestRank(seconds, 0.95),
        seconds.size(),
        seconds.size() + " live analyses (replayed runs excluded); nearest rank",
        extra);
  }

  /** M-20: median and p95 of Σ model-call cost per analysis, priced from token usage. */
  public static MetricResult cost(List<AnalysisCase> cases) {
    List<Double> usd = new ArrayList<>();
    for (AnalysisCase c : cases) {
      if ("ANALYSED".equals(c.outcome())) {
        usd.add(c.costMicros() / 1_000_000.0);
      }
    }
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("medianUsd", Stats.nearestRank(usd, 0.5));
    extra.put("p95Usd", Stats.nearestRank(usd, 0.95));
    return MetricResult.number(
        "M-20",
        Stats.nearestRank(usd, 0.5),
        usd.size(),
        usd.size()
            + " analyses; priced from the token usage of every call, replayed calls at"
            + " their recorded usage",
        extra);
  }

  /** M-16: canary values found in cloud-bound requests (count), over requests scanned. */
  public static MetricResult piiEgress(List<AnalysisCase> cases) {
    long hits = cases.stream().mapToLong(AnalysisCase::canaryHits).sum();
    long requests = cases.stream().mapToLong(AnalysisCase::cloudRequests).sum();
    return MetricResult.count(
        "M-16",
        hits,
        requests,
        requests + " cloud-bound requests scanned for the contracts' PII values",
        Map.of());
  }

  /** M-17: canary hits in the database, logs and temp files after the runs. */
  public static MetricResult contentAtRest(List<PiiCase> all) {
    List<PiiCase> cases = all.stream().filter(c -> "at-rest".equals(c.mode())).toList();
    long hits = cases.stream().mapToLong(PiiCase::atRestHits).sum();
    Set<String> places = new HashSet<>();
    cases.forEach(c -> places.addAll(c.atRestPlaces()));
    return MetricResult.count(
        "M-17",
        hits,
        cases.size(),
        cases.size() + " scans (database rows, captured logs, temp files)",
        Map.of("placesScanned", new ArrayList<>(new java.util.TreeSet<>(places))));
  }

  /** M-25: gold entities fully replaced by a placeholder, over all types; per type in extra. */
  public static MetricResult piiRecall(List<PiiCase> cases, String mode) {
    Map<String, long[]> byType = new TreeMap<>();
    for (PiiCase c : cases) {
      if (!mode.equals(c.mode())) {
        continue;
      }
      c.replacedAndTotalByType()
          .forEach(
              (type, rt) -> {
                long[] sum = byType.computeIfAbsent(type, k -> new long[2]);
                sum[0] += rt.get(0);
                sum[1] += rt.get(1);
              });
    }
    long k = byType.values().stream().mapToLong(v -> v[0]).sum();
    long n = byType.values().stream().mapToLong(v -> v[1]).sum();
    Map<String, Object> perType = new LinkedHashMap<>();
    byType.forEach((t, v) -> perType.put(t, v[0] + " of " + v[1]));
    long contracts = cases.stream().filter(c -> mode.equals(c.mode())).count();
    return MetricResult.proportion(
        "M-25", k, n, contracts + " contracts, redaction mode " + mode, Map.of("byType", perType));
  }

  /** A clause seen by segmentation: printed label and type. */
  public record ClauseView(String label, String type) {}

  /** M-23: boundary counts by printed label, and type accuracy over matched clauses. */
  public record Segmentation(int tp, int fp, int fn, int typeCorrect, int typeMatched) {
    public double f1() {
      return tp == 0 ? 0 : 2.0 * tp / (2.0 * tp + fp + fn);
    }
  }

  public static Segmentation segmentation(List<ClauseView> predicted, List<ClauseView> gold) {
    Map<String, String> p = new LinkedHashMap<>();
    predicted.forEach(c -> p.putIfAbsent(Matching.normaliseLabel(c.label()), c.type()));
    Map<String, String> g = new LinkedHashMap<>();
    gold.forEach(c -> g.putIfAbsent(Matching.normaliseLabel(c.label()), c.type()));
    int tp = 0;
    int correct = 0;
    for (var e : p.entrySet()) {
      if (g.containsKey(e.getKey())) {
        tp++;
        if (g.get(e.getKey()).equals(e.getValue())) {
          correct++;
        }
      }
    }
    return new Segmentation(tp, p.size() - tp, g.size() - tp, correct, tp);
  }
}
