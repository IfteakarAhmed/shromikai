package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.Prompt;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.ai.PromptRenderer;
import bd.shromikai.testsupport.Golden;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rendered prompt of each P6 prompt with a fixed input is a golden file (test-strategy.md §7),
 * so a change to wording, order or escaping shows up in review. The severity rubric of FR-39 is
 * also asserted by name.
 */
@Tag("unit")
@Requirement("FR-39")
class PromptGoldenTest {

  private final PromptRegistry registry = new PromptRegistry(TestGateways.MAPPER);
  private final PromptRenderer renderer = new PromptRenderer(TestGateways.MAPPER);

  /**
   * Insertion-ordered map: {@code Map.of} iterates in a per-JVM order and would make goldens flaky.
   */
  private static Map<String, Object> om(Object... keyValues) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private static final List<Map<String, Object>> CANDIDATES =
      List.of(
          om(
              "sectionKey", "XV-LAB:9@original",
              "actCode", "XV-LAB",
              "authoritativeText", "An employer shall give a worker written notice of dismissal."));

  private static final Map<String, Object> CLAUSE_TEXT =
      om("id", "c4", "text", "The employer may end the contract at once <b>&</b> without notice.");

  private Map<String, Object> inputs(String id) {
    return switch (id) {
      case "clause-facts" -> Map.of("clauses", List.of(CLAUSE_TEXT));
      case "risk-analysis" ->
          Map.of(
              "side", "WORKER",
              "output_language", "bn",
              "profile", om("sector", "READY_MADE_GARMENTS"),
              "outline", List.of(om("clause_id", "c4", "type", "TERMINATION", "facts", List.of())),
              "rule_findings", List.of(),
              "expected_topics_missing", List.of(),
              "candidate_sections", CANDIDATES,
              "clauses", List.of(CLAUSE_TEXT));
      case "risk-citation-repair" ->
          Map.of(
              "finding", om("title", "Dismissal without notice"),
              "reason_codes", List.of("NOT_IN_CANDIDATES"),
              "candidate_sections", CANDIDATES,
              "clauses", List.of(CLAUSE_TEXT));
      case "counter-offer" ->
          Map.of(
              "side",
              "EMPLOYER",
              "output_language",
              "en",
              "finding",
              om("title", "Dismissal without notice"),
              "limits",
              List.of(),
              "previous_attempt_problem",
              "none",
              "clause",
              CLAUSE_TEXT);
      case "verify-support", "verify-support-claude" ->
          Map.of(
              "claim", "An employer must give written notice before dismissal.",
              "quote", "shall give a worker written notice",
              "section_text", "An employer shall give a worker written notice of dismissal.",
              "translation_text", "none");
      default -> throw new IllegalArgumentException(id);
    };
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "clause-facts",
        "risk-analysis",
        "risk-citation-repair",
        "counter-offer",
        "verify-support",
        "verify-support-claude"
      })
  void shouldRenderTheGoldenPrompt(String id) {
    Prompt prompt = registry.get(id, 1);
    PromptRenderer.Rendered rendered = renderer.render(prompt, inputs(id));
    String actual =
        "<system>\n"
            + rendered.system()
            + "\n</system>\n<user>\n"
            + rendered.user().reveal()
            + "\n</user>\n";

    assertThat(actual).isEqualTo(Golden.expect("prompts/" + id + ".v1.txt", actual));
  }

  @Test
  void shouldRenderTheGoldenPromptOfRiskAnalysisV2() {
    Prompt prompt = registry.get("risk-analysis", 2);
    PromptRenderer.Rendered rendered = renderer.render(prompt, inputs("risk-analysis"));
    String actual =
        "<system>\n"
            + rendered.system()
            + "\n</system>\n<user>\n"
            + rendered.user().reveal()
            + "\n</user>\n";

    assertThat(actual).isEqualTo(Golden.expect("prompts/risk-analysis.v2.txt", actual));
    assertThat(prompt.maxOutputTokens()).isEqualTo(3000);
    assertThat(registry.latest("risk-analysis").version()).isEqualTo(2);
    assertThat(registry.get("risk-analysis", 1).maxOutputTokens())
        .as("v1 is kept as it was")
        .isEqualTo(1400);
  }

  @Test
  void shouldRenderTheGoldenPromptOfCounterOfferV2() {
    Prompt prompt = registry.get("counter-offer", 2);
    PromptRenderer.Rendered rendered = renderer.render(prompt, inputs("counter-offer"));
    String actual =
        "<system>\n"
            + rendered.system()
            + "\n</system>\n<user>\n"
            + rendered.user().reveal()
            + "\n</user>\n";

    assertThat(actual).isEqualTo(Golden.expect("prompts/counter-offer.v2.txt", actual));
    assertThat(prompt.maxOutputTokens()).isEqualTo(900);
    assertThat(registry.latest("counter-offer").version()).isEqualTo(2);
    assertThat(registry.get("counter-offer", 1).maxOutputTokens())
        .as("v1 is kept as it was")
        .isEqualTo(500);
  }

  @ParameterizedTest
  @ValueSource(strings = {"risk-citation-repair", "verify-support", "verify-support-claude"})
  void shouldRenderTheGoldenPromptOfV2(String id) {
    Prompt prompt = registry.get(id, 2);
    PromptRenderer.Rendered rendered = renderer.render(prompt, inputs(id));
    String actual =
        "<system>\n"
            + rendered.system()
            + "\n</system>\n<user>\n"
            + rendered.user().reveal()
            + "\n</user>\n";

    assertThat(actual).isEqualTo(Golden.expect("prompts/" + id + ".v2.txt", actual));
    assertThat(registry.latest(id).version()).isEqualTo(2);
    assertThat(prompt.maxOutputTokens()).isEqualTo(id.equals("risk-citation-repair") ? 600 : 250);
  }

  @Test
  void shouldKeepTheSeverityRubricInV2AndTheBoundsInItsText() {
    String system = registry.get("risk-analysis", 2).system();

    assertThat(system)
        .contains(
            "CRITICAL: conflicts with a mandatory statutory provision with loss of wages, benefits or\n  employment protection.")
        .contains(
            "LOW: deviation from an ILO convention or drafting practice without a statutory conflict.")
        .contains("at most 3 findings per request")
        .contains("at most 2 citations per finding")
        .contains("at most 160 characters");
  }

  @Test
  void shouldEmbedTheSeverityRubricVerbatim() {
    String system = registry.get("risk-analysis", 1).system();

    assertThat(system)
        .contains(
            "CRITICAL: conflicts with a mandatory statutory provision with loss of wages, benefits or\n  employment protection.")
        .contains("HIGH: conflicts with a mandatory provision with another effect.")
        .contains(
            "MEDIUM: omission, ambiguity or interaction creating a material risk that a statutory right is not\n  honoured.")
        .contains(
            "LOW: deviation from an ILO convention or drafting practice without a statutory conflict.");
  }

  @Test
  void shouldEscapeUntrustedClauseTextAndNothingElse() {
    Prompt prompt = registry.get("risk-analysis", 1);
    String user = renderer.render(prompt, inputs("risk-analysis")).user().reveal();

    assertThat(user).contains("&lt;b&gt;&amp;&lt;/b&gt;");
    assertThat(user).contains("An employer shall give a worker written notice of dismissal.");
  }

  @Test
  void shouldDeclareDynamicEnumsOnTheCitingPrompts() {
    assertThat(registry.get("risk-analysis", 1).dynamicEnums())
        .extracting(d -> d.path())
        .containsExactly("$.findings[*].citations[*].section_key");
    assertThat(registry.get("risk-citation-repair", 1).dynamicEnums())
        .extracting(d -> d.path())
        .containsExactly("$.citations[*].section_key");
  }
}
