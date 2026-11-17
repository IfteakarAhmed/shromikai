package bd.shromikai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes {@code report.md} and {@code report.json} (evaluation.md §12). Generation fails when a
 * metric of the catalog is missing for ShromikAI, or when the report has no limitations: a metric
 * that was not measured is reported as not available, not done or not run, never dropped (§11).
 */
public final class ReportGenerator {

  /** The metrics of the headline table, baseline beside ShromikAI. */
  static final List<String> HEADLINE =
      List.of("M-01", "M-03", "M-04", "M-11", "M-12", "M-16", "M-19", "M-20");

  private static final ObjectMapper JSON =
      new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

  private ReportGenerator() {}

  /** Writes both files into {@code dir}. */
  public static void write(EvalReport report, Path dir) {
    validate(report);
    try {
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("report.md"), markdown(report), StandardCharsets.UTF_8);
      Files.writeString(
          dir.resolve("report.json"),
          JSON.writeValueAsString(json(report)) + "\n",
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Writes the latest report and keeps a copy of it, with its configuration, under runs/. */
  public static void writeRun(EvalReport report, Path reportsDir) {
    write(report, reportsDir);
    Path run = reportsDir.resolve("runs").resolve(runFolder(report.config()));
    write(report, run);
    try {
      Files.writeString(
          run.resolve("config.json"),
          JSON.writeValueAsString(report.config()) + "\n",
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String runFolder(EvalReport.Config config) {
    return config.generatedAt().substring(0, 10) + "-" + config.runId();
  }

  static void validate(EvalReport report) {
    Map<String, MetricResult> shromikai = report.metricsBySystem().get("shromikai");
    if (shromikai == null) {
      throw new IllegalStateException("The report has no metrics for shromikai");
    }
    for (String id : MetricCatalog.ids()) {
      if (!shromikai.containsKey(id)) {
        throw new IllegalStateException(
            "Metric " + id + " is missing: report it as not available, not done or not run");
      }
    }
    for (var system : report.metricsBySystem().values()) {
      for (MetricResult m : system.values()) {
        if (m.status() == MetricResult.Status.MEASURED
            && m.ci95() != null
            && (m.count() == null || m.n() == null)) {
          throw new IllegalStateException(m.id() + " is a proportion without its count");
        }
      }
    }
    if (report.limitations().isEmpty()) {
      throw new IllegalStateException("Limitations are empty: every report states its limitations");
    }
  }

  // ------------------------------------------------------------------ JSON

  static ObjectNode json(EvalReport report) {
    ObjectNode root = JSON.createObjectNode();
    EvalReport.Config c = report.config();
    ObjectNode provenance = root.putObject("provenance");
    provenance.put("gitSha", c.gitSha());
    provenance.put("generatedAt", c.generatedAt());
    provenance.put("corpusRelease", c.corpusRelease());
    provenance.put("generatedBy", "./gradlew evaluate (bd.shromikai.eval); never edited by hand");
    ObjectNode config = JSON.valueToTree(c);
    config.put("runDir", "runs/" + runFolder(c));
    root.set("config", config);
    ArrayNode headline = root.putArray("headline");
    for (String id : HEADLINE) {
      ObjectNode row = headline.addObject();
      row.put("id", id);
      row.put("metric", MetricCatalog.get(id).name());
      MetricResult base = report.metricsBySystem().getOrDefault("baseline", Map.of()).get(id);
      MetricResult ours = report.metricsBySystem().get("shromikai").get(id);
      row.put("baseline", base == null ? "not applicable" : base.display());
      row.put("shromikai", ours.display());
      row.put("target", MetricCatalog.get(id).target());
      if (ours.met() == null) {
        row.putNull("met");
      } else {
        row.put("met", ours.met());
      }
    }
    ObjectNode metrics = root.putObject("metrics");
    report
        .metricsBySystem()
        .forEach(
            (system, byId) -> {
              ObjectNode s = metrics.putObject(system);
              byId.forEach((id, m) -> s.set(id, JSON.valueToTree(m)));
            });
    root.set("ablations", JSON.valueToTree(report.ablations()));
    ObjectNode bias = root.putObject("bias");
    var ours = report.metricsBySystem().get("shromikai");
    bias.set("M-13", JSON.valueToTree(ours.get("M-13")));
    bias.set("M-14", JSON.valueToTree(ours.get("M-14")));
    root.set("failures", JSON.valueToTree(report.failures()));
    root.set("limitations", JSON.valueToTree(report.limitations()));
    root.set("knownFailures", JSON.valueToTree(report.knownFailures()));
    root.set("errata", JSON.valueToTree(report.errata()));
    return root;
  }

  // ------------------------------------------------------------------ Markdown

  private static String esc(String s) {
    return s.replace("|", "\\|").replace("\n", " ");
  }

  private static String money(long micros) {
    return String.format(Locale.ROOT, "US$%.4f", micros / 1_000_000.0);
  }

  static String markdown(EvalReport report) {
    EvalReport.Config c = report.config();
    StringBuilder md = new StringBuilder();
    md.append("# Evaluation report ").append(c.runId()).append("\n\n");
    md.append(
        "_This file is generated by the evaluation runner; do not edit by hand (R-07). Mistakes"
            + " found later go into Errata._\n\n");
    md.append("## Configuration\n\n");
    md.append("- Set / split / systems / runs: ")
        .append(c.set())
        .append(" / ")
        .append(c.split())
        .append(" / ")
        .append(String.join(", ", c.systems()))
        .append(" / ")
        .append(c.runs())
        .append('\n');
    md.append("- Contracts: ").append(String.join(", ", c.contracts())).append('\n');
    md.append("- Git SHA: ")
        .append(c.gitSha())
        .append(c.gitClean() ? " (clean tree)" : " (tree not clean)")
        .append("; corpus release ")
        .append(c.corpusRelease())
        .append("; generated ")
        .append(c.generatedAt())
        .append("; seed ")
        .append(c.seed())
        .append('\n');
    md.append("- Hardware: ").append(c.hardware()).append('\n');
    md.append("- Models per prompt: ");
    c.models().forEach((role, model) -> md.append(role).append(" = ").append(model).append("; "));
    md.append('\n');
    md.append("- Verifier independence: ").append(c.verifierIndependence()).append('\n');
    md.append("- Prompt versions: ");
    c.promptVersions().forEach((id, v) -> md.append(id).append(" v").append(v).append("; "));
    md.append('\n');
    md.append("- Live spend of this run (ledger): ")
        .append(money(c.liveSpendMicros()))
        .append("; ledger total after the run: ")
        .append(money(c.ledgerTotalMicros()))
        .append('\n');
    md.append("- Data: ").append(c.dataNote()).append('\n');
    md.append("- Labels: ")
        .append(c.reviewedLabelFiles())
        .append(" reviewed labels (")
        .append(c.reviewedLabelFiles())
        .append(" of ")
        .append(c.labelFiles())
        .append(" label files reviewed)\n");
    md.append("- Adversarial citations: ")
        .append(c.adversarialCases())
        .append(" cases, ")
        .append(c.reviewedAdversarial())
        .append(" reviewed by a person\n\n");

    md.append("## Headline: baseline vs ShromikAI\n\n");
    md.append("| Metric | Baseline | ShromikAI | Difference | Target | Met |\n");
    md.append("| --- | --- | --- | --- | --- | --- |\n");
    for (String id : HEADLINE) {
      MetricResult base = report.metricsBySystem().getOrDefault("baseline", Map.of()).get(id);
      MetricResult ours = report.metricsBySystem().get("shromikai").get(id);
      md.append("| ")
          .append(id)
          .append(' ')
          .append(esc(MetricCatalog.get(id).name()))
          .append(" | ")
          .append(base == null ? "not applicable" : esc(base.display()))
          .append(" | ")
          .append(esc(ours.display()))
          .append(" | ")
          .append("no interval: fewer than 10 contracts (indicative)")
          .append(" | ")
          .append(esc(String.valueOf(MetricCatalog.get(id).target())))
          .append(" | ")
          .append(met(ours))
          .append(" |\n");
    }
    md.append('\n');

    md.append("## All metrics (per run and mean)\n\n");
    md.append("One run per system; there is no mean or range to report.\n\n");
    md.append(
        "| ID | Metric | System | Result | CI95 (Wilson) | Target | Met | Basis | Details |\n");
    md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
    report
        .metricsBySystem()
        .forEach(
            (system, byId) ->
                byId.values()
                    .forEach(
                        m ->
                            md.append("| ")
                                .append(m.id())
                                .append(" | ")
                                .append(esc(m.name()))
                                .append(" | ")
                                .append(system)
                                .append(" | ")
                                .append(esc(m.display()))
                                .append(" | ")
                                .append(
                                    m.ci95() == null
                                        ? ""
                                        : String.format(
                                            Locale.ROOT,
                                            "%.3f to %.3f",
                                            m.ci95().get(0),
                                            m.ci95().get(1)))
                                .append(" | ")
                                .append(esc(String.valueOf(m.target())))
                                .append(" | ")
                                .append(met(m))
                                .append(" | ")
                                .append(esc(m.basis()))
                                .append(" | ")
                                .append(esc(details(m)))
                                .append(" |\n")));
    md.append('\n');

    md.append("## Ablations\n\n");
    if (report.ablations().isEmpty()) {
      md.append("None run.\n");
    }
    for (EvalReport.Ablation a : report.ablations()) {
      md.append("- **").append(a.name()).append("** (").append(a.how()).append(")");
      a.values().forEach((k, v) -> md.append("; ").append(k).append(": ").append(v));
      md.append('\n');
    }
    md.append('\n');

    md.append("## Bias: bilingual consistency and side balance\n\n");
    var ours = report.metricsBySystem().get("shromikai");
    md.append("- M-13: ").append(ours.get("M-13").display()).append('\n');
    md.append("- M-14: ").append(ours.get("M-14").display()).append("\n\n");

    md.append("## Failures and abstentions (case IDs, reason codes)\n\n");
    list(md, report.failures(), "None.");
    md.append("## Limitations\n\n");
    list(md, report.limitations(), "None stated.");
    md.append("## Known failures\n\n");
    list(md, report.knownFailures(), "None found.");
    md.append("## Errata\n\n");
    list(md, report.errata(), "None.");
    return md.toString();
  }

  private static String met(MetricResult m) {
    return m.met() == null ? "" : m.met() ? "yes" : "no (missed)";
  }

  private static String details(MetricResult m) {
    StringBuilder out = new StringBuilder();
    m.extra().forEach((k, v) -> out.append(k).append('=').append(v).append("; "));
    if (m.note() != null && m.status() == MetricResult.Status.MEASURED) {
      out.append(m.note());
    }
    return out.toString().replace("%", " percent");
  }

  private static void list(StringBuilder md, List<String> items, String empty) {
    if (items.isEmpty()) {
      md.append(empty).append("\n\n");
      return;
    }
    items.forEach(i -> md.append("- ").append(i).append('\n'));
    md.append('\n');
  }
}
