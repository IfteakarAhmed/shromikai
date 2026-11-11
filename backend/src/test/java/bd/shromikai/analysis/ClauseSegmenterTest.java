package bd.shromikai.analysis;

import static bd.shromikai.testsupport.TestGateways.gateway;
import static bd.shromikai.testsupport.TestGateways.json;
import static bd.shromikai.testsupport.TestGateways.port;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.ingest.IntakeProperties;
import bd.shromikai.ingest.IntakeResult;
import bd.shromikai.ingest.IntakeService;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-33: rule-based clause splitting and the model fallback. */
@Tag("unit")
@Requirement("FR-33")
class ClauseSegmenterTest {

  static List<String> contracts() {
    return SyntheticContracts.IDS;
  }

  private static List<Para> paras(String text) {
    IntakeResult read =
        new IntakeService(IntakeProperties.defaults(), image -> null).fromText(text);
    List<Para> out = new ArrayList<>();
    read.paragraphs().forEach(p -> out.add(new Para(p.id(), p.text(), Set.of())));
    return out;
  }

  private static Para para(String id, String text) {
    return new Para(id, SensitiveText.of(text), Set.of());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldSplitTheSyntheticContractIntoTheExpectedClauseCount(String id) {
    Optional<List<Clause>> clauses =
        ClauseSegmenter.byNumbering(paras(SyntheticContracts.text(id)));

    assertThat(clauses).isPresent();
    assertThat(clauses.get()).hasSize(SyntheticContracts.expectedClauseCount(id));
    assertThat(clauses.get().get(0).label()).as("opening text before clause 1").isNull();
    assertThat(clauses.get().get(1).label()).matches("(1|১)[.)]");
    assertThat(clauses.get()).extracting(Clause::id).startsWith("c1", "c2");
  }

  @Test
  void shouldKeepSubItemsInsideTheirParentClause() {
    List<Clause> clauses =
        ClauseSegmenter.byNumbering(paras(SyntheticContracts.text("syn-en-01"))).orElseThrow();

    Clause appointment = clauses.get(1);
    assertThat(appointment.text().reveal())
        .contains("(a) The Employer appoints", "(b) The Worker agrees");
    assertThat(appointment.paragraphIds()).hasSize(3);
  }

  @Test
  void shouldCoverEveryParagraphExactlyOnceInOrder() {
    List<Para> paragraphs = paras(SyntheticContracts.text("syn-bn-01"));

    List<Clause> clauses = ClauseSegmenter.byNumbering(paragraphs).orElseThrow();

    List<String> covered = clauses.stream().flatMap(c -> c.paragraphIds().stream()).toList();
    assertThat(covered).isEqualTo(paragraphs.stream().map(Para::id).toList());
  }

  @Test
  void shouldSplitOnParenthesisedLettersWhenThereAreNoNumbers() {
    List<Para> paragraphs =
        List.of(
            para("p1", "(a) The Worker joins as an operator."),
            para("p2", "(b) The wage is paid monthly."),
            para("p3", "i. sub point about the wage."),
            para("p4", "(c) Either side may end it."));

    List<Clause> clauses = ClauseSegmenter.byNumbering(paragraphs).orElseThrow();

    assertThat(clauses).hasSize(3);
    assertThat(clauses.get(1).paragraphIds()).containsExactly("p2", "p3");
  }

  @Test
  void shouldCarryParagraphFlagsOntoTheClause() {
    List<Para> paragraphs =
        List.of(
            para("p1", "1. First clause."),
            new Para(
                "p2",
                SensitiveText.of("2. Second clause."),
                Set.of(ClauseFlag.INSTRUCTION_LIKE_TEXT)));

    List<Clause> clauses = ClauseSegmenter.byNumbering(paragraphs).orElseThrow();

    assertThat(clauses.get(0).flags()).isEmpty();
    assertThat(clauses.get(1).flags()).containsExactly(ClauseFlag.INSTRUCTION_LIKE_TEXT);
  }

  @Test
  void shouldNotSplitWithFewerThanTwoNumberedClauses() {
    assertThat(
            ClauseSegmenter.byNumbering(
                List.of(para("p1", "Plain text without numbers."), para("p2", "1. only one"))))
        .isEmpty();
  }

  @Test
  void shouldAskTheModelToGroupUnnumberedText() {
    ChatPort cloud =
        port(
            "anthropic",
            r ->
                json(
                    "{\"status\":\"OK\",\"reason\":\"NONE\",\"clauses\":["
                        + "{\"paragraph_ids\":[\"p1\",\"p2\"]},{\"paragraph_ids\":[\"p3\"]}]}"));
    var segmenter = new ClauseSegmenter(gateway(cloud));

    List<Clause> clauses =
        segmenter.split(
            List.of(para("p1", "Heading"), para("p2", "Body"), para("p3", "More")), null);

    assertThat(clauses).hasSize(2);
    assertThat(clauses.get(0).paragraphIds()).containsExactly("p1", "p2");
  }

  @Test
  void shouldFallBackToOneClausePerParagraphWhenTheModelAnswerIsInconsistent() {
    ChatPort cloud =
        port(
            "anthropic",
            r ->
                json(
                    "{\"status\":\"OK\",\"reason\":\"NONE\",\"clauses\":["
                        + "{\"paragraph_ids\":[\"p1\",\"p9\"]}]}"));
    var segmenter = new ClauseSegmenter(gateway(cloud));

    List<Clause> clauses = segmenter.split(List.of(para("p1", "A"), para("p2", "B")), null);

    assertThat(clauses).hasSize(2);
  }
}
