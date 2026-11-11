package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifyFixtures;
import bd.shromikai.verify.VerifyProperties;
import bd.shromikai.verify.internal.SupportVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-53: obligations are verified like findings; an unverified statutory item is dropped. */
@Tag("unit")
@Requirement("FR-53")
class ObligationsAgentTest {

  private static final String ANSWER =
      """
      {"status":"OK","reason":"NONE","items":[
       {"party":"EMPLOYER","obligation":"Give written notice of dismissal.","trigger":"Dismissal",
        "deadline":{"value":14,"unit":"DAYS"},"source":"BOTH","status":"STATED_IN_CONTRACT",
        "clause_ids":["c1"],"citations":[{"section_key":"XV-LAB:9@original",
        "quote":"shall give a worker written notice of dismissal at least fourteen days",
        "claim":"An employer must give notice before dismissal."}]},
       {"party":"EMPLOYER","obligation":"Pay wages monthly.","trigger":null,"deadline":null,
        "source":"STATUTE","status":"STATUTORY_NOT_IN_CONTRACT","clause_ids":["c1"],
        "citations":[{"section_key":"XV-LAB:12@original",
        "quote":"this quote is not in the section text at all","claim":"Wages are monthly."}]},
       {"party":"WORKER","obligation":"Report for duty on time.","trigger":null,"deadline":null,
        "source":"CONTRACT","status":"STATED_IN_CONTRACT","clause_ids":["c1","c99"],
        "citations":[]}]}
      """;

  private final List<ChatRequest> log = new CopyOnWriteArrayList<>();

  private ObligationsAgent agent(ModelGateway gateway) {
    var verifier =
        new CitationVerifier(
            new VerifyFixtures.Corpus(),
            new SupportVerifier(gateway, AnalysisFixtures.models()),
            VerifyProperties.defaults(),
            Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));
    return new ObligationsAgent(verifier);
  }

  private ModelGateway gateway() {
    return AnalysisFixtures.gateway(
        r ->
            r.promptId().equals("obligations")
                ? TestGateways.json(ANSWER)
                : TestGateways.json(
                    "{\"label\":\"SUPPORTS\",\"confidence\":0.92,\"reason\":\"stand-in\"}"),
        log);
  }

  private static CandidateSet candidates() {
    CandidateSet set = new CandidateSet(40);
    for (String key : List.of(AnalysisFixtures.KEY_NOTICE, AnalysisFixtures.KEY_OTHER)) {
      set.add(
          new CandidateSet.Candidate(
              key, "XV-LAB", "Fictional act", key, null, "en", "text", null, false));
    }
    return set;
  }

  private static List<RiskAgent.ClauseView> clauses() {
    return List.of(
        new RiskAgent.ClauseView(
            AnalysisFixtures.clause(
                "c1", ClauseType.TERMINATION, "The employer may end the contract."),
            List.of()));
  }

  private static VerificationContext context() {
    return new VerificationContext(
        java.time.LocalDate.of(2026, 11, 16),
        Set.of("XV"),
        "2026.11.01-1",
        Set.of(AnalysisFixtures.KEY_NOTICE, AnalysisFixtures.KEY_OTHER),
        1);
  }

  @Test
  void aStatutoryItemWithAFailedCitationIsDroppedAndOthersKeepTheirVerifiedOnes() {
    ModelGateway gateway = gateway();
    ModelSession session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);
    ObligationsAgent agent = agent(gateway);

    List<ObligationItem> proposed =
        agent.propose(session, AnalysisRequest.of(Side.WORKER), clauses(), candidates());
    List<ObligationItem> verified = agent.verify(proposed, context());

    assertThat(proposed).hasSize(3);
    assertThat(verified).extracting(ObligationItem::obligation).hasSize(2);
    assertThat(verified.get(0).citations()).hasSize(1);
    assertThat(verified.get(0).claims()).isEmpty();
    assertThat(verified.get(1).source()).isEqualTo("CONTRACT");
    assertThat(verified).extracting(ObligationItem::id).containsExactly("o1", "o2");
  }

  @Test
  void unknownClauseIdsAreIgnoredAndTheDeadlineIsKept() {
    ModelGateway gateway = gateway();
    ModelSession session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    List<ObligationItem> proposed =
        agent(gateway).propose(session, AnalysisRequest.of(Side.WORKER), clauses(), candidates());

    assertThat(proposed.get(2).clauseIds()).containsExactly("c1");
    assertThat(proposed.get(0).deadline()).isEqualTo(new ObligationItem.Deadline(14, "DAYS"));
    assertThat(ObligationsAgent.unitOf(proposed.get(0))).isEqualTo("DAYS");
    assertThat(ObligationsAgent.unitOf(proposed.get(1))).isNull();
  }

  @Test
  void theCallsOnlyCandidateKeysAreOfferedToTheModel() {
    ModelGateway gateway = gateway();
    ModelSession session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    agent(gateway).propose(session, AnalysisRequest.of(Side.WORKER), clauses(), candidates());

    assertThat(log).extracting(ChatRequest::promptId).contains("obligations");
  }
}
