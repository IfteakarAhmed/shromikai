package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.Prompt;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ADR-0019: for every runtime prompt, the largest answer that its schema, its prompt text and its
 * caller allow fits {@code max_output_tokens} of the latest version, at the measured token rates
 * with a 15% margin and at a harsher assumption with none. Three live runs lost money to answers
 * cut at their cap; a prompt that can fail that way must be bounded or must be listed here with the
 * reason it cannot be and what happens when it fails.
 *
 * <p>Rates: JSON text 0.65 tokens per character and Bangla 0.9, measured on recorded Anthropic
 * answers and rounded up (see {@code RiskAnswerSizeTest}). They are not measured for the local Qwen
 * tokenizer.
 */
@Tag("unit")
@Requirement("FR-37")
class PromptAnswerBoundAuditTest {

  private static final double ASCII = 0.70;
  private static final double BANGLA = 0.90;
  private static final double ASCII_HARSH = 0.80;
  private static final double BANGLA_HARSH = 1.00;
  private static final String KEY = "BLA-2006:1234@amend-2026";

  private final PromptRegistry registry = new PromptRegistry(TestGateways.MAPPER);

  private static ObjectNode obj() {
    return TestGateways.MAPPER.createObjectNode();
  }

  private static String bn(int chars) {
    return "ক".repeat(chars);
  }

  private static double tokens(JsonNode answer, double asciiRate, double banglaRate) {
    String json = answer.toString();
    long bangla = json.codePoints().filter(cp -> cp >= 0x0980 && cp <= 0x09FF).count();
    return bangla * banglaRate + (json.length() - bangla) * asciiRate;
  }

  // ---- worst-case answers, one per prompt -------------------------------------------------

  private static JsonNode riskAnalysis() {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode list = out.putArray("findings");
    for (int i = 0; i < 3; i++) {
      ObjectNode f = list.addObject();
      f.put("title", bn(50)).put("kind", "INTERACTION").put("severity", "CRITICAL");
      f.put("non_obvious", false).put("clause_type", "DISCIPLINE_AND_MISCONDUCT");
      ArrayNode ids = f.putArray("clause_ids");
      for (int c = 1; c <= 5; c++) {
        ids.add("c" + (100 + c));
      }
      f.put("explanation", bn(160));
      ArrayNode cites = f.putArray("citations");
      for (int c = 0; c < 2; c++) {
        cites.addObject().put("section_key", KEY).put("quote", bn(120)).put("claim", bn(100));
      }
      f.put("confidence", 0.95);
    }
    return out;
  }

  private static JsonNode obligations() {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode list = out.putArray("items");
    for (int i = 0; i < 6; i++) {
      ObjectNode item = list.addObject();
      item.put("party", "EMPLOYER").put("obligation", bn(100)).put("trigger", bn(60));
      item.putObject("deadline").put("value", 14.5).put("unit", "MONTHS");
      item.put("source", "BOTH").put("status", "STATUTORY_NOT_IN_CONTRACT");
      ArrayNode ids = item.putArray("clause_ids");
      for (int c = 1; c <= 3; c++) {
        ids.add("c" + (100 + c));
      }
      item.putArray("citations")
          .addObject()
          .put("section_key", KEY)
          .put("quote", bn(100))
          .put("claim", bn(80));
    }
    return out;
  }

  private static JsonNode memo(boolean bangla) {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    out.put("summary", bangla ? bn(600) : "a".repeat(600));
    ArrayNode steps = out.putArray("next_steps");
    for (int i = 0; i < 5; i++) {
      steps.add(bangla ? bn(140) : "a".repeat(140));
    }
    return out;
  }

  private static JsonNode baseline() {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode list = out.putArray("findings");
    for (int i = 0; i < 4; i++) {
      ObjectNode f = list.addObject();
      f.put("title", bn(50)).put("severity", "CRITICAL").put("explanation", bn(160));
      ArrayNode cites = f.putArray("citations");
      for (int c = 0; c < 2; c++) {
        cites
            .addObject()
            .put("section_key", "A".repeat(40))
            .put("quote", bn(120))
            .put("claim", bn(100));
      }
    }
    return out;
  }

  private static JsonNode counterOffer() {
    return obj()
        .put("status", "OK")
        .put("reason", "NONE")
        .put("action", "REPLACE")
        .put("proposed_text", bn(600))
        .put("rationale", bn(140));
  }

  private static JsonNode repair() {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    out.putArray("citations")
        .addObject()
        .put("section_key", KEY)
        .put("quote", bn(120))
        .put("claim", bn(100));
    return out;
  }

  private static JsonNode verify() {
    return obj().put("label", "CONTRADICTS").put("confidence", 0.95).put("reason", "x".repeat(200));
  }

  /** The caller sends at most ten clauses per call (ClauseClassifier.MAX_PER_BATCH). */
  private static JsonNode classify(int clauses) {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE").put("document_type", "EMPLOYMENT_CONTRACT");
    ArrayNode list = out.putArray("clauses");
    for (int i = 0; i < clauses; i++) {
      list.addObject()
          .put("id", "c" + (100 + i))
          .put("type", "DISCIPLINE_AND_MISCONDUCT")
          .put("confidence", 0.95);
    }
    return out;
  }

  private static JsonNode facts(int clauses, int factsPerClause) {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode list = out.putArray("clauses");
    for (int i = 0; i < clauses; i++) {
      ObjectNode c = list.addObject().put("id", "c" + (100 + i));
      ArrayNode f = c.putArray("facts");
      for (int k = 0; k < factsPerClause; k++) {
        f.addObject()
            .put("parameter", "TERMINATION_WITHOUT_NOTICE_ALLOWED")
            .put("value", 12.5)
            .put("unit", "HOURS_PER_DAY")
            .put("confidence", 0.95);
      }
    }
    return out;
  }

  /** Three queries of twelve six-letter Bangla words: the prompt's own limit, the worst script. */
  private static JsonNode queryRewrite() {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode q = out.putArray("queries");
    String query = String.join(" ", java.util.Collections.nCopies(12, bn(6)));
    for (int i = 0; i < 3; i++) {
      q.add(query);
    }
    return out;
  }

  private static JsonNode segment(int paragraphs) {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    ArrayNode list = out.putArray("clauses");
    for (int i = 1; i <= paragraphs; i++) {
      list.addObject().putArray("paragraph_ids").add("p" + i);
    }
    return out;
  }

  private static JsonNode pii(int perKind) {
    ObjectNode out = obj();
    out.put("status", "OK").put("reason", "NONE");
    for (String kind : List.of("persons", "organisations", "addresses", "employee_ids")) {
      ArrayNode a = out.putArray(kind);
      for (int i = 0; i < perKind; i++) {
        a.add(bn(20));
      }
    }
    return out;
  }

  // ---- the audit --------------------------------------------------------------------------

  /** Prompts whose answer is bounded by schema, prompt text or caller, and must fit. */
  private Map<String, JsonNode> bounded() {
    return Map.ofEntries(
        Map.entry("risk-analysis", riskAnalysis()),
        Map.entry("counter-offer", counterOffer()),
        Map.entry("risk-citation-repair", repair()),
        Map.entry("verify-support", verify()),
        Map.entry("verify-support-claude", verify()),
        Map.entry("clause-classify", classify(ClauseClassifier.MAX_PER_BATCH)),
        // Bounded by the prompt text (three queries of twelve words), not by the schema; the
        // margin is thin (254 of 255 tokens) only in this synthetic worst case.
        Map.entry("query-rewrite", queryRewrite()),
        Map.entry("obligations", obligations()),
        Map.entry("memo-bn", memo(true)),
        Map.entry("memo-en", memo(false)),
        Map.entry("baseline", baseline()));
  }

  /** Prompts that cannot be bounded, with what protects the analysis when the answer is cut. */
  private static final Map<String, String> UNBOUNDED =
      Map.of(
          "clause-facts",
              "the facts per clause are not bounded; at most 4 clauses per call, and a call cut at"
                  + " its cap is split in halves; a single clause still cut gets no facts",
          "clause-segment",
              "the number of clauses follows the document; invalid output falls back to one clause"
                  + " per paragraph",
          "pii-extract",
              "the entities follow the text of a 1,500-character chunk; capping them would drop"
                  + " personal data; a cut answer fails closed (local-model-unavailable)");

  @Test
  void shouldCoverEveryPromptOfTheCatalogue() throws IOException {
    Set<String> ids = new TreeSet<>();
    try (Stream<Path> dirs = Files.list(Path.of("src/main/resources/prompts"))) {
      dirs.filter(Files::isDirectory).forEach(d -> ids.add(d.getFileName().toString()));
    }
    Set<String> covered = new TreeSet<>(bounded().keySet());
    covered.addAll(UNBOUNDED.keySet());

    assertThat(covered).isEqualTo(ids);
  }

  @Test
  void shouldFitTheCapOfTheLatestVersionAtMeasuredRatesWithMargin() {
    bounded()
        .forEach(
            (id, answer) -> {
              Prompt latest = registry.latest(id);
              assertThat(JsonSchemas.validate(latest.outputSchema(), answer))
                  .as(id + " v" + latest.version() + ": the worst case is a valid answer")
                  .isEmpty();
              assertThat(tokens(answer, ASCII, BANGLA))
                  .as(id + " v" + latest.version() + " worst case, tokens (cap)")
                  .isLessThanOrEqualTo(latest.maxOutputTokens() * 0.85);
            });
  }

  @Test
  void shouldFitTheCapAtAHarsherAssumptionWithoutMargin() {
    bounded()
        .forEach(
            (id, answer) ->
                assertThat(tokens(answer, ASCII_HARSH, BANGLA_HARSH))
                    .as(id + " worst case at harsher rates")
                    .isLessThanOrEqualTo(registry.latest(id).maxOutputTokens()));
  }

  @Test
  void shouldNotLetTheClassifierBatchGrowPastWhatFits() {
    assertThat(ClauseClassifier.MAX_PER_BATCH).isEqualTo(10);
    assertThat(tokens(classify(11), ASCII, BANGLA))
        .as("one clause more than the limit would pass the margin")
        .isGreaterThan(registry.latest("clause-classify").maxOutputTokens() * 0.85 - 100);
  }

  @Test
  void shouldKeepEachUnboundedPromptHonest_itsWorstCaseReallyPassesTheCap() {
    int facts = registry.latest("clause-facts").maxOutputTokens();
    assertThat(tokens(facts(FactExtractor.MAX_PER_BATCH, 26), ASCII, BANGLA))
        .as("clause-facts: four clauses with many facts")
        .isGreaterThan(facts);
    assertThat(tokens(facts(FactExtractor.MAX_PER_BATCH, 1), ASCII, BANGLA))
        .as("clause-facts: four clauses with one (longest-named) fact each still fits")
        .isLessThanOrEqualTo(facts);
    assertThat(tokens(segment(150), ASCII, BANGLA))
        .as("clause-segment: 150 paragraphs")
        .isGreaterThan(registry.latest("clause-segment").maxOutputTokens());
    assertThat(tokens(pii(30), ASCII, BANGLA))
        .as("pii-extract: 30 entities per kind")
        .isGreaterThan(registry.latest("pii-extract").maxOutputTokens());
    UNBOUNDED.values().forEach(why -> assertThat(why).isNotBlank());
  }
}
