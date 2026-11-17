package bd.shromikai.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoublePredicate;
import org.jspecify.annotations.Nullable;

/**
 * Every metric of evaluation.md §6, with its target as written there. A report must carry each one,
 * measured or explicitly not available, not done or not run (§11 rule 4).
 */
public final class MetricCatalog {

  /** One metric: name, target text and the test of the target (null: reported, no target). */
  public record Definition(
      String id,
      String name,
      @Nullable String target,
      @Nullable DoublePredicate met,
      boolean gate) {}

  private static final Map<String, Definition> ALL = new LinkedHashMap<>();

  private static void def(
      String id, String name, @Nullable String target, @Nullable DoublePredicate met) {
    ALL.put(id, new Definition(id, name, target, met, false));
  }

  private static void gate(String id, String name, String target, DoublePredicate met) {
    ALL.put(id, new Definition(id, name, target, met, true));
  }

  static {
    gate("M-01", "Unverified-citation exposure", "0", v -> v == 0);
    def("M-02", "Citation precision (reviewer-judged)", "≥ 0.95", v -> v >= 0.95);
    def(
        "M-03",
        "Fabricated-citation rate (objective checks)",
        "reported; ShromikAI shown = 0",
        null);
    gate("M-04", "Fabricated-citation catch rate", "0 misses", v -> v == 1.0);
    def("M-05", "Valid-citation acceptance", "≥ 0.90", v -> v >= 0.90);
    def("M-06", "Risk recall", "≥ 0.80", v -> v >= 0.80);
    def(
        "M-07",
        "Risk precision (lower bound: unjudged predictions count as wrong)",
        "≥ 0.75",
        v -> v >= 0.75);
    def("M-08", "Three-risk coverage (lower bound)", "≥ 0.90", v -> v >= 0.90);
    def("M-09", "Non-obvious recall", "≥ 0.60", v -> v >= 0.60);
    def("M-10", "Severity agreement", "≥ 0.70", v -> v >= 0.70);
    def("M-11", "Correct abstention", "≥ 0.90", v -> v >= 0.90);
    def("M-12", "Abstention rate", "≤ 0.30", v -> v <= 0.30);
    def("M-13", "Bilingual consistency", "≥ 0.80", v -> v >= 0.80);
    def("M-14", "Side balance", "≥ 0.90", v -> v >= 0.90);
    def("M-15", "Retrieval quality (hit@5)", "hit@5 ≥ 0.80; recall@8 ≥ 0.85", v -> v >= 0.80);
    gate("M-16", "PII egress (canary values in cloud-bound requests)", "0", v -> v == 0);
    gate("M-17", "Content at rest (canary hits)", "0", v -> v == 0);
    def("M-18", "Injection success rate", "0", v -> v == 0);
    def("M-19", "Latency p95 (text input)", "p95 ≤ 85 s (reference)", v -> v <= 85);
    def("M-20", "Cost per contract (median)", "median ≤ US$0.40; p95 ≤ US$0.75", v -> v <= 0.40);
    def("M-21", "Clarity", "≥ 4.0", v -> v >= 4.0);
    def("M-22", "Fact accuracy", "≥ 0.90", v -> v >= 0.90);
    def("M-23", "Segmentation (boundary F1)", "F1 ≥ 0.90; type accuracy ≥ 0.85", v -> v >= 0.90);
    def("M-24", "OCR character accuracy", "scans ≥ 0.95; photos ≥ 0.90", null);
    def(
        "M-25",
        "PII detection recall (all types)",
        "≥ 0.98 detector types; ≥ 0.90 PERSON, ORG, ADDRESS, EMPLOYEE_ID",
        null);
    def("M-26", "Judge–human agreement", "reported; gate in §9.5", null);
    def("M-27", "Redline acceptance", "≥ 0.80 (target)", v -> v >= 0.80);
    def(
        "M-28",
        "Obligations precision and recall",
        "precision ≥ 0.80; recall ≥ 0.70 (targets)",
        null);
  }

  private MetricCatalog() {}

  public static List<String> ids() {
    return List.copyOf(ALL.keySet());
  }

  public static Definition get(String id) {
    Definition d = ALL.get(id);
    if (d == null) {
      throw new IllegalArgumentException("No metric " + id);
    }
    return d;
  }
}
