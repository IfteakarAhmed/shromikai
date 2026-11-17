package bd.shromikai.eval;

import bd.shromikai.analysis.ClauseType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Label files {@code eval/labels/{contractId}.yaml} (evaluation.md §4.2, §9). A file is validated
 * against the label schema; a {@code draft} names no reviewer and a {@code reviewed} file names at
 * least one, with role and date. Labels drafted by an AI are drafts until people review them.
 */
public final class Labels {

  private static final YAMLMapper YAML = new YAMLMapper();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> CLAUSE_TYPES =
      Arrays.stream(ClauseType.values()).map(Enum::name).collect(Collectors.toSet());
  private static final JsonNode SCHEMA = schema();

  private Labels() {}

  /** A gold risk (evaluation.md §4.2). */
  public record GoldRisk(
      String id,
      List<String> clauseLabels,
      String clauseType,
      String kind,
      String severity,
      boolean nonObvious,
      String side,
      List<String> acceptableSectionKeys,
      boolean requiresCitation) {}

  /** A gold clause: printed label and type. */
  public record GoldClause(@Nullable String label, String clauseType) {}

  /** A reviewer entry. */
  public record Reviewer(String name, String role, String date) {}

  /** One label file. */
  public record LabelFile(
      String contractId,
      String status,
      String draftedBy,
      List<Reviewer> reviewedBy,
      String corpusRelease,
      List<GoldClause> clauses,
      List<GoldRisk> risks,
      int obligations) {

    public boolean reviewed() {
      return "reviewed".equals(status) && !reviewedBy.isEmpty();
    }
  }

  /** How many files there are and how many are reviewed. */
  public record Summary(int files, int reviewed) {}

  private static JsonNode schema() {
    try (InputStream in = Labels.class.getResourceAsStream("/schemas/eval/labels.schema.json")) {
      if (in == null) {
        throw new IllegalStateException("labels.schema.json is missing from the eval resources");
      }
      return JSON.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Loads and validates one file; any problem stops the run with a message. */
  public static LabelFile load(Path file) {
    JsonNode node;
    try {
      node = YAML.readTree(Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<String> errors = new ArrayList<>(schemaErrors(node));
    if (errors.isEmpty()) {
      String status = node.path("status").asText();
      boolean hasReviewers = node.path("reviewed_by").isArray();
      if (status.equals("reviewed") && !hasReviewers) {
        errors.add("status reviewed needs reviewed_by with name, role and date");
      }
      if (status.equals("draft") && hasReviewers) {
        errors.add("a draft must have reviewed_by: null; set status: reviewed with the reviewers");
      }
      String expected = node.path("contractId").asText() + ".yaml";
      if (!file.getFileName().toString().equals(expected)) {
        errors.add(
            "file name " + file.getFileName() + " differs from contractId (" + expected + ")");
      }
      for (JsonNode c : node.path("clauses")) {
        checkType(c.path("clauseType").asText(), "clauses", errors);
      }
      for (JsonNode r : node.path("risks")) {
        checkType(r.path("clauseType").asText(), "risks " + r.path("id").asText(), errors);
      }
    }
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(file.getFileName() + ": " + String.join("; ", errors));
    }
    return toFile(node);
  }

  /** Schema errors with the location of the offending value, for example "$.risks[0].kind". */
  private static List<String> schemaErrors(JsonNode node) {
    return REGISTRY.getSchema(SCHEMA).validate(node).stream()
        .map(e -> e.getInstanceLocation() + ": " + e.getMessage())
        .sorted()
        .toList();
  }

  private static final com.networknt.schema.SchemaRegistry REGISTRY =
      com.networknt.schema.SchemaRegistry.withDefaultDialect(
          com.networknt.schema.SpecificationVersion.DRAFT_2020_12);

  private static void checkType(String type, String where, List<String> errors) {
    if (!CLAUSE_TYPES.contains(type)) {
      errors.add(where + ": clauseType " + type + " is not a ClauseType");
    }
  }

  private static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(v -> out.add(v.asText()));
    return out;
  }

  private static LabelFile toFile(JsonNode node) {
    List<Reviewer> reviewers = new ArrayList<>();
    node.path("reviewed_by")
        .forEach(
            r ->
                reviewers.add(
                    new Reviewer(
                        r.path("name").asText(),
                        r.path("role").asText(),
                        r.path("date").asText())));
    List<GoldClause> clauses = new ArrayList<>();
    node.path("clauses")
        .forEach(
            c ->
                clauses.add(
                    new GoldClause(
                        c.path("label").isNull() ? null : c.path("label").asText(),
                        c.path("clauseType").asText())));
    List<GoldRisk> risks = new ArrayList<>();
    node.path("risks")
        .forEach(
            r ->
                risks.add(
                    new GoldRisk(
                        r.path("id").asText(),
                        strings(r.path("clauseLabels")),
                        r.path("clauseType").asText(),
                        r.path("kind").asText(),
                        r.path("severity").asText(),
                        r.path("nonObvious").asBoolean(),
                        r.path("side").asText(),
                        strings(r.path("acceptableSectionKeys")),
                        r.path("requiresCitation").asBoolean())));
    return new LabelFile(
        node.path("contractId").asText(),
        node.path("status").asText(),
        node.path("drafted_by").asText(),
        reviewers,
        node.path("corpusRelease").asText(),
        clauses,
        risks,
        node.path("obligations").size());
  }

  /** Loads every {@code *.yaml} in a directory, sorted by name. */
  public static List<LabelFile> loadAll(Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (var s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith(".yaml"))
          .sorted()
          .map(Labels::load)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Summary summarise(List<LabelFile> files) {
    return new Summary(files.size(), (int) files.stream().filter(LabelFile::reviewed).count());
  }

  /** The CSV export for reviewers ({@code eval/labels/export/labels.csv}), one row per risk. */
  public static String csv(List<LabelFile> files) {
    StringBuilder out =
        new StringBuilder(
            "contractId,status,riskId,clauseLabels,clauseType,kind,severity,nonObvious,side,"
                + "acceptableSectionKeys,requiresCitation,reviewerVerdict,reviewerNote\n");
    for (LabelFile f : files) {
      for (GoldRisk r : f.risks()) {
        out.append(
                String.join(
                    ",",
                    cell(f.contractId()),
                    cell(f.status()),
                    cell(r.id()),
                    cell(String.join(";", r.clauseLabels())),
                    cell(r.clauseType()),
                    cell(r.kind()),
                    cell(r.severity()),
                    String.valueOf(r.nonObvious()),
                    cell(r.side()),
                    cell(String.join(";", r.acceptableSectionKeys())),
                    String.valueOf(r.requiresCitation()),
                    "",
                    ""))
            .append('\n');
      }
    }
    return out.toString();
  }

  private static String cell(String value) {
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }
}
