package bd.shromikai.rules;

import bd.shromikai.rules.Rule.LegalBasis;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.shared.text.QuoteMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads a legal rules file (architecture §9.1, FR-36). Every rule is validated against {@code
 * rules.schema.json}. For each {@code active} rule the loader also checks that {@code reviewed_by}
 * is present and differs from {@code authored_by}, that the section exists and is current, and that
 * the quote passes the quote match against the section's authoritative text; any failure throws
 * {@link RuleLoadException} and the application does not start. A {@code draft} rule is never
 * evaluated; its quote problems are only reported so the author can fix them before review.
 */
public final class RuleLoader {

  /** Minimum similarity of an active rule's quote (architecture §7.7). */
  public static final double QUOTE_THRESHOLD = 0.92;

  /** A current section of the corpus. */
  public record CurrentSection(String sectionKey, String authoritativeText) {}

  /** The corpus as the loader sees it: the current version of one section of one act. */
  public interface CorpusLookup {
    Optional<CurrentSection> current(String actCode, String sectionNo);
  }

  /** All rules in file order and the problems found in drafts (never fatal). */
  public record Result(List<Rule> rules, List<String> draftProblems) {

    public Result {
      rules = List.copyOf(rules);
      draftProblems = List.copyOf(draftProblems);
    }

    public List<Rule> activeRules() {
      return rules.stream().filter(Rule::active).toList();
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemas SCHEMAS = new JsonSchemas();

  private RuleLoader() {}

  /**
   * @param verifyQuotes check active rules against the corpus; false only while no corpus release
   *     exists yet, so the first ingestion can run
   */
  public static Result load(Path file, CorpusLookup corpus, boolean verifyQuotes) {
    if (!Files.isRegularFile(file)) {
      return new Result(List.of(), List.of());
    }
    Object root;
    try (InputStream in = Files.newInputStream(file)) {
      root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String name = file.getFileName().toString();
    if (root == null) {
      return new Result(List.of(), List.of());
    }
    JsonNode document = MAPPER.valueToTree(root);
    List<String> schemaErrors = SCHEMAS.validate("schemas/rules.schema.json", document);
    if (!schemaErrors.isEmpty()) {
      throw new RuleLoadException(name + ": " + schemaErrors.getFirst());
    }
    List<Rule> rules = new ArrayList<>();
    List<String> draftProblems = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode node : document) {
      String id = node.get("id").asText();
      if (!ids.add(id)) {
        throw new RuleLoadException(name + ": duplicate rule id " + id);
      }
      rules.add(rule(name, node, corpus, verifyQuotes, draftProblems));
    }
    return new Result(rules, draftProblems);
  }

  private static Rule rule(
      String file, JsonNode n, CorpusLookup corpus, boolean verifyQuotes, List<String> drafts) {
    String id = n.get("id").asText();
    String where = file + " rule " + id;
    FactParameter fact = FactParameter.valueOf(n.get("fact").asText());
    RuleComparator comparator = RuleComparator.valueOf(n.get("comparator").asText());
    Double value = n.get("value").isNull() ? null : n.get("value").asDouble();
    Unit unit = n.get("unit").isNull() ? null : Unit.valueOf(n.get("unit").asText());
    checkShape(where, fact, comparator, value, unit);
    Rule.Status status = Rule.Status.valueOf(n.get("status").asText().toUpperCase());
    String authoredBy = n.get("authored_by").asText();
    String reviewedBy = text(n, "reviewed_by");
    JsonNode basis = n.get("legal_basis");
    LegalBasis legalBasis =
        new LegalBasis(
            basis.get("act").asText(), basis.get("section").asText(), basis.get("quote").asText());

    String sectionKey = null;
    List<String> problems = new ArrayList<>();
    if (verifyQuotes) {
      Optional<CurrentSection> section = corpus.current(legalBasis.act(), legalBasis.section());
      if (section.isEmpty()) {
        problems.add(
            where
                + ": "
                + legalBasis.act()
                + " section "
                + legalBasis.section()
                + " is not a current section of the corpus");
      } else {
        sectionKey = section.get().sectionKey();
        if (QuoteMatcher.similarity(legalBasis.quote(), section.get().authoritativeText())
            < QUOTE_THRESHOLD) {
          problems.add(where + ": the quote is not found in " + sectionKey);
        }
      }
    }
    Rule rule =
        new Rule(
            id,
            n.get("clause_type").asText(),
            fact,
            comparator,
            value,
            unit,
            text(n, "applies_to_category"),
            legalBasis,
            Severity.valueOf(n.get("severity").asText()),
            n.get("explanation_bn").asText(),
            n.get("explanation_en").asText(),
            authoredBy,
            reviewedBy,
            text(n, "review_note"),
            status,
            sectionKey);
    if (status == Rule.Status.ACTIVE) {
      if (!rule.twoPersonReviewed()) {
        throw new RuleLoadException(
            where + ": an active rule needs reviewed_by, and it must differ from authored_by");
      }
      if (!problems.isEmpty()) {
        throw new RuleLoadException(problems.getFirst());
      }
    } else {
      drafts.addAll(problems);
    }
    return rule;
  }

  private static void checkShape(
      String where,
      FactParameter fact,
      RuleComparator comparator,
      @Nullable Double value,
      @Nullable Unit unit) {
    switch (comparator) {
      case MIN, MAX, EQ -> {
        if (value == null || unit == null) {
          throw new RuleLoadException(where + ": " + comparator + " needs a value and a unit");
        }
        if (!fact.allowedUnits().contains(unit)) {
          throw new RuleLoadException(where + ": " + unit + " is not a unit of " + fact);
        }
      }
      case REQUIRED -> {
        if (value != null) {
          throw new RuleLoadException(where + ": REQUIRED takes no value");
        }
      }
      case PROHIBITED -> {
        if (!fact.isBoolean()) {
          throw new RuleLoadException(where + ": PROHIBITED needs a boolean fact");
        }
      }
    }
  }

  private static @Nullable String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().strip();
  }
}
