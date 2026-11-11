package bd.shromikai.analysis;

import static bd.shromikai.analysis.AnalysisFixtures.clause;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.ClauseFact;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-35: model facts cross-checked by regex; disagreement lowers confidence by 0.3. */
@Tag("unit")
@Requirement("FR-35")
class FactExtractorTest {

  private final FactExtractor extractor = new FactExtractor();
  private final List<ChatRequest> log = new CopyOnWriteArrayList<>();

  /** The model "reads" the facts the test decides, per clause ID. */
  private ModelSession session(Map<String, List<Object[]>> factsByClause) {
    return AnalysisFixtures.session(
        AnalysisFixtures.gateway(
            request -> {
              ObjectNode out = TestGateways.MAPPER.createObjectNode();
              out.put("status", "OK").put("reason", "NONE");
              ArrayNode clauses = out.putArray("clauses");
              for (String id : factsByClause.keySet()) {
                if (request.user().reveal().contains("\"id\":\"" + id + "\"")) {
                  ObjectNode clause = clauses.addObject().put("id", id);
                  ArrayNode facts = clause.putArray("facts");
                  for (Object[] f : factsByClause.get(id)) {
                    facts
                        .addObject()
                        .put("parameter", (String) f[0])
                        .put("value", (Double) f[1])
                        .put("unit", (String) f[2])
                        .put("confidence", (Double) f[3]);
                  }
                }
              }
              return out;
            },
            log));
  }

  private ClauseFact extract(String text, Object[] fact) {
    ModelSession session = session(Map.of("c1", List.<Object[]>of(fact)));
    List<ClauseFact> facts =
        extractor
            .extract(session, Stage.RULES, List.of(clause("c1", ClauseType.OTHER, text)))
            .get("c1");
    return facts.isEmpty() ? null : facts.getFirst();
  }

  @Test
  void shouldKeepConfidence_whenTheRegexFindsTheSameNumber() {
    ClauseFact fact =
        extract(
            "The employer shall give 30 days notice.",
            new Object[] {"NOTICE_BY_EMPLOYER", 30.0, "DAYS", 0.9});

    assertThat(fact.regexAgrees()).isTrue();
    assertThat(fact.confidence()).isEqualTo(0.9);
  }

  @Test
  void shouldLowerConfidenceByThreeTenths_whenTheRegexFindsADifferentNumber() {
    ClauseFact fact =
        extract(
            "The employer shall give 30 days notice.",
            new Object[] {"NOTICE_BY_EMPLOYER", 7.0, "DAYS", 0.9});

    assertThat(fact.regexAgrees()).isFalse();
    assertThat(fact.confidence()).isEqualTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void shouldNeverLowerConfidenceBelowZero() {
    ClauseFact fact =
        extract("Notice of 30 days.", new Object[] {"NOTICE_BY_EMPLOYER", 7.0, "DAYS", 0.2});

    assertThat(fact.confidence()).isZero();
  }

  @Test
  void shouldLeaveRegexAgreesNull_whenTheClauseHasNoNumberOfThatKind() {
    ClauseFact fact =
        extract(
            "Reasonable notice shall be given.",
            new Object[] {"NOTICE_BY_EMPLOYER", 14.0, "DAYS", 0.8});

    assertThat(fact.regexAgrees()).isNull();
    assertThat(fact.confidence()).isEqualTo(0.8);
  }

  @Test
  void shouldAgreeAcrossWeeksAndDays() {
    ClauseFact fact =
        extract(
            "The worker gives 2 weeks written notice.",
            new Object[] {"NOTICE_BY_WORKER", 14.0, "DAYS", 0.9});

    assertThat(fact.regexAgrees()).isTrue();
  }

  @Test
  void shouldReadBanglaDigitsAndUnitWords() {
    ClauseFact fact =
        extract(
            "মালিক ৩০ দিনের নোটিশ দিবেন।", new Object[] {"NOTICE_BY_EMPLOYER", 30.0, "DAYS", 0.9});

    assertThat(fact.regexAgrees()).isTrue();
  }

  @Test
  void shouldCheckHoursPercentMoneyAndMultipliers() {
    assertThat(
            extract(
                    "Work is 48 hours per week.",
                    new Object[] {"WEEKLY_WORKING_HOURS", 48.0, "HOURS_PER_WEEK", 0.9})
                .regexAgrees())
        .isTrue();
    assertThat(
            extract(
                    "Overtime is paid at double the rate.",
                    new Object[] {"OVERTIME_RATE", 2.0, "MULTIPLIER", 0.9})
                .regexAgrees())
        .isTrue();
    assertThat(
            extract(
                    "Basic wage is BDT 25,000 per month.",
                    new Object[] {"BASIC_WAGE", 25000.0, "BDT_PER_MONTH", 0.9})
                .regexAgrees())
        .isTrue();
    assertThat(
            extract(
                    "Deductions up to 5% of wages.",
                    new Object[] {"DEDUCTION_LIMIT", 10.0, "PERCENT", 0.9})
                .regexAgrees())
        .isFalse();
  }

  @Test
  void shouldNotApplyARegexToBooleanFacts() {
    ClauseFact fact =
        extract(
            "Overtime is compulsory for 3 days a week.",
            new Object[] {"OVERTIME_COMPULSORY", 1.0, "BOOLEAN", 0.9});

    assertThat(fact.regexAgrees()).isNull();
  }

  @Test
  void shouldDropAFactWhoseUnitDoesNotBelongToItsParameter() {
    ClauseFact fact =
        extract("Annual leave is 20 days.", new Object[] {"ANNUAL_LEAVE", 20.0, "PERCENT", 0.9});

    assertThat(fact).isNull();
  }

  @Test
  void shouldRunAtMostThreeBatchesInParallelAndKeepClauseOrder() {
    List<Clause> clauses = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      clauses.add(clause("c" + i, ClauseType.OTHER, "Clause number " + i + " text."));
    }
    ModelSession session = session(Map.of());

    Map<String, List<ClauseFact>> facts = extractor.extract(session, Stage.RULES, clauses);

    assertThat(log).hasSize(3);
    assertThat(facts.keySet()).containsExactlyElementsOf(clauses.stream().map(Clause::id).toList());
  }

  @Test
  void shouldSendOnlyRedactedClauseTextWithTheJobsEgressContext() {
    ModelSession session = session(Map.of());
    extractor.extract(
        session, Stage.RULES, List.of(clause("c1", ClauseType.OTHER, "Pay [PERSON_1] 30 days.")));

    assertThat(log).hasSize(1);
    assertThat(log.getFirst().egressContext()).isSameAs(session.egress());
    assertThat(log.getFirst().user().reveal()).contains("[PERSON_1]");
    assertThat(log.getFirst().promptId()).isEqualTo("clause-facts");
  }
}
