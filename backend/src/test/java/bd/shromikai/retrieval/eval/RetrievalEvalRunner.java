package bd.shromikai.retrieval.eval;

import bd.shromikai.retrieval.Retrieval;
import bd.shromikai.retrieval.RetrievalMetrics;
import bd.shromikai.retrieval.RetrievalMetrics.Outcome;
import bd.shromikai.retrieval.RetrievalMetrics.Summary;
import bd.shromikai.retrieval.RetrievalResult;
import bd.shromikai.retrieval.RetrievalService;
import bd.shromikai.retrieval.SearchOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the retrieval ablation (FR-23) through {@link RetrievalService}, the production code path,
 * and computes every number with {@link RetrievalMetrics}. Nothing in the report is typed by hand
 * (R-07): the Markdown and JSON are rendered from the measured outcomes.
 *
 * <p>The topic map is deliberately off in every row: it was curated from the same section headings
 * the questions were written from, so counting it would measure the curation, not retrieval.
 */
public final class RetrievalEvalRunner {

  /** One question of {@code eval/retrieval/questions.jsonl} (evaluation.md §4.3). */
  public record Question(
      String id,
      String language,
      String question,
      String clauseType,
      List<String> expected,
      String status) {}

  /** The five ablation rows. */
  public enum Strategy {
    VECTOR_ONLY("Vector only"),
    TEXT_ONLY("Text only"),
    HYBRID("Hybrid (RRF)"),
    HYBRID_GRAPH("Hybrid + graph"),
    HYBRID_GRAPH_REWRITE("Hybrid + graph + rewrite");

    final String label;

    Strategy(String label) {
      this.label = label;
    }

    SearchOptions options() {
      SearchOptions base = new SearchOptions(SearchOptions.BD, 8, true, true, false, false, null);
      return switch (this) {
        case VECTOR_ONLY -> base.withText(false);
        case TEXT_ONLY -> base.withVector(false);
        case HYBRID -> base;
        case HYBRID_GRAPH -> base.withGraph(true);
        case HYBRID_GRAPH_REWRITE -> base.withGraph(true).withRewrite(true);
      };
    }
  }

  /** Per-strategy measurements; {@code skippedReason} is set when the row could not run. */
  public record Row(
      Strategy strategy,
      String skippedReason,
      List<Outcome> outcomes,
      Summary all,
      Summary checked,
      Summary bengali,
      Summary english,
      int rewritten,
      double meanMillis) {}

  /** Facts that go in the report header. */
  public record Context(
      String gitSha,
      String generatedAt,
      String corpusRelease,
      String embeddingModel,
      String rewriteModel,
      String rewritePrompt,
      int embeddedSections,
      String rewriteSkippedReason) {}

  private final RetrievalService retrieval;
  private final ObjectMapper mapper = new ObjectMapper();

  public RetrievalEvalRunner(RetrievalService retrieval) {
    this.retrieval = retrieval;
  }

  public static List<Question> load(Path file) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    List<Question> questions = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode n = mapper.readTree(line);
      List<String> expected = new ArrayList<>();
      n.get("expectedSectionKeys").forEach(k -> expected.add(k.asText()));
      questions.add(
          new Question(
              n.get("id").asText(),
              n.get("language").asText(),
              n.get("question").asText(),
              n.path("clauseType").asText(),
              expected,
              n.get("status").asText()));
    }
    return questions;
  }

  /**
   * @param rewriteSkippedReason non-null to skip the rewrite row (no cloud key), never faked
   */
  public List<Row> run(List<Question> questions, String rewriteSkippedReason) {
    List<Row> rows = new ArrayList<>();
    for (Strategy strategy : Strategy.values()) {
      if (strategy == Strategy.HYBRID_GRAPH_REWRITE && rewriteSkippedReason != null) {
        rows.add(skipped(strategy, rewriteSkippedReason));
        continue;
      }
      List<Outcome> outcomes = new ArrayList<>();
      int rewritten = 0;
      long nanos = 0;
      for (Question q : questions) {
        long start = System.nanoTime();
        Retrieval result = retrieval.search(q.question(), strategy.options());
        nanos += System.nanoTime() - start;
        if (result.keywordOnly()) {
          throw new IllegalStateException(
              "the embedding model was unavailable for "
                  + q.id()
                  + "; the vector rows would be void");
        }
        if (result.rewritten()) {
          rewritten++;
        }
        outcomes.add(
            new Outcome(
                result.results().stream().map(RetrievalResult::sectionKey).toList(),
                Set.copyOf(q.expected())));
      }
      rows.add(
          new Row(
              strategy,
              null,
              outcomes,
              RetrievalMetrics.summarise(outcomes),
              RetrievalMetrics.summarise(
                  filter(questions, outcomes, q -> q.status().equals("checked"))),
              RetrievalMetrics.summarise(
                  filter(questions, outcomes, q -> q.language().equals("bn"))),
              RetrievalMetrics.summarise(
                  filter(questions, outcomes, q -> q.language().equals("en"))),
              rewritten,
              questions.isEmpty() ? 0 : nanos / 1e6 / questions.size()));
    }
    return rows;
  }

  private static Row skipped(Strategy strategy, String reason) {
    Summary none = RetrievalMetrics.summarise(List.of());
    return new Row(strategy, reason, List.of(), none, none, none, none, 0, 0);
  }

  private static List<Outcome> filter(
      List<Question> questions,
      List<Outcome> outcomes,
      java.util.function.Predicate<Question> keep) {
    List<Outcome> out = new ArrayList<>();
    for (int i = 0; i < questions.size(); i++) {
      if (keep.test(questions.get(i))) {
        out.add(outcomes.get(i));
      }
    }
    return out;
  }

  // ---------------------------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------------------------

  public String markdown(Context ctx, List<Question> questions, List<Row> rows) {
    long checked = questions.stream().filter(q -> q.status().equals("checked")).count();
    long bn = questions.stream().filter(q -> q.language().equals("bn")).count();
    StringBuilder md = new StringBuilder();
    md.append("# Retrieval evaluation (FR-23, M-15)\n\n");
    md.append("- Generated: ")
        .append(ctx.generatedAt())
        .append(" at git ")
        .append(ctx.gitSha())
        .append('\n');
    md.append(
        "- Generated by `RetrievalEvalRunnerIT` (`./gradlew evalRetrieval`); every number below is computed by `RetrievalMetrics` from the runs (R-07).\n");
    md.append("- Corpus release: ")
        .append(ctx.corpusRelease())
        .append(" (")
        .append(ctx.embeddedSections())
        .append(" sections embedded with ")
        .append(ctx.embeddingModel())
        .append(")\n");
    md.append("- Questions: ")
        .append(questions.size())
        .append(" (")
        .append(bn)
        .append(" Bangla, ")
        .append(questions.size() - bn)
        .append(" English); status `checked`: ")
        .append(checked)
        .append(" of ")
        .append(questions.size())
        .append(". ");
    if (checked < questions.size()) {
      md.append(
          "**Every question is still `draft` (drafted from the statute text, not yet reviewed by a team member), so the numbers below are over draft questions and are not reportable as reviewed results.**");
    }
    md.append('\n');
    md.append("- Query rewriting: ");
    if (ctx.rewriteSkippedReason() != null) {
      md.append("**not run**: ").append(ctx.rewriteSkippedReason());
    } else {
      md.append(ctx.rewriteModel()).append(", prompt ").append(ctx.rewritePrompt());
    }
    md.append(
        "\n- Topic map: off in every row (it was curated from the same headings the questions come from).\n");
    md.append(
        "- Candidate lists: top 8 fused results; the graph rows add at most 4 context sections. hit@5 and MRR@10 read the ranked list, recall@8 reads the whole final candidate list (evaluation.md M-15). Targets (not results): hit@5 ≥ 0.80, recall@8 ≥ 0.85.\n\n");

    md.append("## Ablation (all questions)\n\n");
    md.append(
        "| Method | hit@5 | MRR@10 | recall@8 | mean ms/query |\n| --- | ---: | ---: | ---: | ---: |\n");
    for (Row row : rows) {
      if (row.skippedReason() != null) {
        md.append("| ")
            .append(row.strategy().label)
            .append(" | not run | not run | not run | not run |\n");
        continue;
      }
      Summary s = row.all();
      md.append("| ")
          .append(row.strategy().label)
          .append(" | ")
          .append(fmt(s.hitAt5()))
          .append(" (")
          .append(s.hitsAt5())
          .append('/')
          .append(s.questions())
          .append(") | ")
          .append(fmt(s.mrrAt10()))
          .append(" | ")
          .append(fmt(s.recallAt8()))
          .append(" | ")
          .append(String.format("%.0f", row.meanMillis()))
          .append(" |\n");
    }
    md.append(
        "\nLatency includes query embedding (bge-m3 on local Ollama, cached per normalised query) and SQL; the rewrite row also includes the model call.\n");

    md.append(
        "\n## By question language\n\n| Method | Bangla hit@5 | Bangla MRR@10 | English hit@5 | English MRR@10 |\n| --- | ---: | ---: | ---: | ---: |\n");
    for (Row row : rows) {
      if (row.skippedReason() != null) {
        continue;
      }
      md.append("| ")
          .append(row.strategy().label)
          .append(" | ")
          .append(fmt(row.bengali().hitAt5()))
          .append(" (")
          .append(row.bengali().hitsAt5())
          .append('/')
          .append(row.bengali().questions())
          .append(") | ")
          .append(fmt(row.bengali().mrrAt10()))
          .append(" | ")
          .append(fmt(row.english().hitAt5()))
          .append(" (")
          .append(row.english().hitsAt5())
          .append('/')
          .append(row.english().questions())
          .append(") | ")
          .append(fmt(row.english().mrrAt10()))
          .append(" |\n");
    }

    md.append("\n## Checked questions only\n\n");
    if (checked == 0) {
      md.append("No question is `checked` yet (0 of ")
          .append(questions.size())
          .append("), so there is nothing to report here.\n");
    } else {
      md.append("| Method | hit@5 | MRR@10 | recall@8 |\n| --- | ---: | ---: | ---: |\n");
      for (Row row : rows) {
        if (row.skippedReason() == null) {
          Summary s = row.checked();
          md.append("| ")
              .append(row.strategy().label)
              .append(" | ")
              .append(fmt(s.hitAt5()))
              .append(" (")
              .append(s.hitsAt5())
              .append('/')
              .append(s.questions())
              .append(") | ")
              .append(fmt(s.mrrAt10()))
              .append(" | ")
              .append(fmt(s.recallAt8()))
              .append(" |\n");
        }
      }
    }

    Row best = bestSingle(rows);
    Row graph = row(rows, Strategy.HYBRID_GRAPH);
    md.append("\n## Acceptance check (FR-23)\n\n");
    String verdict =
        graph.all().hitsAt5() >= best.all().hitsAt5()
            ? "**Met**: hybrid + graph hit@5 = "
                + graph.all().hitsAt5()
                + '/'
                + graph.all().questions()
                + ", best single method ("
                + best.strategy().label
                + ") = "
                + best.all().hitsAt5()
                + '/'
                + best.all().questions()
                + '.'
            : "**Not met**: hybrid + graph hit@5 = "
                + graph.all().hitsAt5()
                + '/'
                + graph.all().questions()
                + " is below the best single method ("
                + best.strategy().label
                + ") = "
                + best.all().hitsAt5()
                + '/'
                + best.all().questions()
                + ". See the questions below and the phase report.";
    md.append(verdict).append("\n\n");

    md.append("## Questions where hybrid + graph misses the top 5\n\n");
    List<String> misses = new ArrayList<>();
    for (int i = 0; i < questions.size(); i++) {
      Outcome o = graph.outcomes().get(i);
      if (!RetrievalMetrics.hitAt(5, o)) {
        misses.add(
            "- "
                + questions.get(i).id()
                + " ("
                + questions.get(i).language()
                + "): expected "
                + String.join(", ", questions.get(i).expected())
                + "; top 3 returned: "
                + String.join(", ", o.ranked().subList(0, Math.min(3, o.ranked().size()))));
      }
    }
    md.append(misses.isEmpty() ? "None.\n" : String.join("\n", misses) + "\n");
    return md.toString();
  }

  public ObjectNode json(Context ctx, List<Question> questions, List<Row> rows) {
    ObjectNode root = mapper.createObjectNode();
    root.put("gitSha", ctx.gitSha());
    root.put("generatedAt", ctx.generatedAt());
    root.put("corpusRelease", ctx.corpusRelease());
    root.put("embeddingModel", ctx.embeddingModel());
    root.put("embeddedSections", ctx.embeddedSections());
    root.put("rewriteModel", ctx.rewriteModel());
    root.put("rewritePrompt", ctx.rewritePrompt());
    root.put("questions", questions.size());
    root.put(
        "questionsChecked", questions.stream().filter(q -> q.status().equals("checked")).count());
    ArrayNode out = root.putArray("rows");
    for (Row row : rows) {
      ObjectNode n = out.addObject();
      n.put("method", row.strategy().name());
      if (row.skippedReason() != null) {
        n.put("skipped", row.skippedReason());
        continue;
      }
      n.put("questions", row.all().questions());
      n.put("hitsAt5", row.all().hitsAt5());
      n.put("hitAt5", row.all().hitAt5());
      n.put("mrrAt10", row.all().mrrAt10());
      n.put("recallAt8", row.all().recallAt8());
      n.put("meanMillis", row.meanMillis());
      n.put("rewritten", row.rewritten());
    }
    return root;
  }

  public void write(Path dir, Context ctx, List<Question> questions, List<Row> rows)
      throws IOException {
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve("retrieval.md"), markdown(ctx, questions, rows), StandardCharsets.UTF_8);
    Files.writeString(
        dir.resolve("retrieval.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json(ctx, questions, rows))
            + "\n",
        StandardCharsets.UTF_8);
  }

  static Row row(List<Row> rows, Strategy strategy) {
    return rows.stream().filter(r -> r.strategy() == strategy).findFirst().orElseThrow();
  }

  /** The best of vector only and text only by hit@5 (ties: more MRR). */
  static Row bestSingle(List<Row> rows) {
    Row vector = row(rows, Strategy.VECTOR_ONLY);
    Row text = row(rows, Strategy.TEXT_ONLY);
    if (vector.all().hitsAt5() != text.all().hitsAt5()) {
      return vector.all().hitsAt5() > text.all().hitsAt5() ? vector : text;
    }
    return vector.all().mrrAt10() >= text.all().mrrAt10() ? vector : text;
  }

  private static String fmt(double value) {
    return String.format("%.3f", value);
  }

  /** For tests: a summary table by strategy name. */
  static Map<String, Summary> summaries(List<Row> rows) {
    Map<String, Summary> out = new LinkedHashMap<>();
    rows.forEach(r -> out.put(r.strategy().name(), r.all()));
    return out;
  }
}
