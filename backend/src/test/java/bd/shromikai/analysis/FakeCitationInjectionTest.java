package bd.shromikai.analysis;

import static bd.shromikai.analysis.AnalysisFixtures.KEY_NOTICE;
import static bd.shromikai.analysis.AnalysisFixtures.KEY_OTHER;
import static bd.shromikai.analysis.AnalysisFixtures.clause;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifiedCitation;
import bd.shromikai.verify.VerifyFixtures;
import bd.shromikai.verify.VerifyProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-46, R-01: the risk agent stub cites section "999" with an invented quote. The verifier rejects
 * it, the corrective loop runs exactly once, the finding ends UNCERTAIN with no citation and
 * exactly one review item exists. This is the scene {@code scripts/demo-fake-citation.sh} shows.
 */
@Tag("unit")
@Requirement("FR-46")
class FakeCitationInjectionTest {

  private static final String FAKE_KEY = "XV-LAB:999@original";
  private static final String FAKE_QUOTE =
      "every worker is entitled to a company car after one year";

  private final List<ChatRequest> log = new CopyOnWriteArrayList<>();

  private Finding plantedFinding() {
    return new Finding(
        "f1",
        SensitiveText.of("Dismissal without notice"),
        FindingKind.CONFLICT,
        Severity.HIGH,
        false,
        FindingSource.LLM_ANALYSIS,
        "TERMINATION",
        List.of("c4"),
        SensitiveText.of("The clause lets the employer end the job at once."),
        SensitiveText.of("A worker could lose pay without warning."),
        0.8,
        null,
        List.of(
            ClaimedCitation.llm(
                FAKE_KEY,
                FAKE_QUOTE,
                SensitiveText.of("A worker is entitled to a company car."),
                "en")),
        FindingVerification.UNCERTAIN,
        List.of(),
        List.of(),
        null,
        null);
  }

  private CitationVerifier verifier() {
    var models = VerifyFixtures.models(null);
    return VerifyFixtures.verifier(
        new VerifyFixtures.Corpus(),
        VerifyFixtures.gateway(
            models,
            new VerifyFixtures.SupportPort(
                "anthropic", r -> VerifyFixtures.verdict("SUPPORTS", 0.99))),
        models);
  }

  private static VerificationContext context() {
    return new VerificationContext(
        VerifyFixtures.TODAY, Set.of("XV"), "2026.11.01-1", Set.of(KEY_NOTICE), 1);
  }

  /** The repair model answers with a real section and another invented quote. */
  private ModelSession session() {
    return AnalysisFixtures.session(
        AnalysisFixtures.gateway(
            request ->
                TestGateways.json(
                    "{\"status\":\"OK\",\"reason\":\"NONE\",\"citations\":[{\"section_key\":\""
                        + KEY_OTHER
                        + "\",\"quote\":\"workers may keep any machine they operate after hours\","
                        + "\"claim\":\"A worker may keep the machine after hours.\"}]}"),
            log));
  }

  @Test
  void shouldRejectTheFakeSectionRunTheLoopOnceAndLeaveOneUncertainFindingWithOneReviewItem() {
    CitationVerifier verifier = verifier();
    FindingVerifier findingVerifier = new FindingVerifier(verifier);
    AnalysisFixtures.StubSource source =
        new AnalysisFixtures.StubSource(query -> List.of(KEY_NOTICE, KEY_OTHER));
    CandidateRetriever retriever = new CandidateRetriever(source, AnalysisFixtures.sections());
    CorrectiveLoop loop =
        new CorrectiveLoop(
            source, retriever, findingVerifier, verifier, VerifyProperties.defaults());
    CandidateSet candidates = new CandidateSet(40);
    candidates.add(AnalysisFixtures.candidateOf(KEY_NOTICE).orElseThrow());
    ModelSession session = session();

    // VERIFY: the fake section does not exist.
    FindingVerifier.Round first = findingVerifier.verify(List.of(plantedFinding()), context());
    Finding afterVerify = first.findings().getFirst();
    assertThat(first.verdicts())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.status().name()).isEqualTo("REJECTED");
              assertThat(v.reasonCodes()).containsExactly("SECTION_NOT_FOUND");
              assertThat(v.attempt()).isEqualTo(1);
            });
    assertThat(afterVerify.verification()).isEqualTo(FindingVerification.UNCERTAIN);
    assertThat(afterVerify.citations()).isEmpty();
    assertThat(loop.needed(first.findings())).isTrue();

    // CORRECT: re-retrieve, ask once more, verify as attempt 2.
    CorrectiveLoop.Round corrected =
        loop.correct(
            session,
            AnalysisRequest.of(Side.WORKER),
            first.findings(),
            List.of(clause("c4", ClauseType.TERMINATION, "The employer may dismiss at once.")),
            candidates,
            context());
    Finding finalFinding = corrected.findings().getFirst();

    assertThat(source.queries).as("the loop retrieved once").hasSize(1);
    assertThat(log.stream().filter(r -> r.promptId().equals("risk-citation-repair")))
        .as("the loop asked the agent once")
        .hasSize(1);
    assertThat(corrected.corrections())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.repaired()).isFalse();
              assertThat(c.newSections()).isEqualTo(1); // KEY_OTHER joined the candidates
            });
    assertThat(finalFinding.verification()).isEqualTo(FindingVerification.UNCERTAIN);
    assertThat(finalFinding.citations()).as("no citation reaches the user").isEmpty();
    assertThat(finalFinding.reasonCodes()).contains("SECTION_NOT_FOUND", "QUOTE_NOT_FOUND@2");

    // REVIEW: exactly one item for the finding.
    AnalysisFixtures.MemoryReviewStore store = new AnalysisFixtures.MemoryReviewStore();
    ReviewQueue queue =
        new ReviewQueue(
            new bd.shromikai.verify.ReviewItemFactory(java.time.Clock.systemUTC()), store);
    ReviewQueue.Opened opened =
        queue.open(
            UUID.randomUUID(),
            Side.WORKER,
            corrected.findings(),
            List.of(),
            Map.of("c4", clause("c4", ClauseType.TERMINATION, "The employer may dismiss at once.")),
            Map.of(),
            candidates.keys());
    assertThat(store.items).hasSize(1);
    assertThat(opened.findings().getFirst().reviewItemId()).isEqualTo(store.items.getFirst().id());
    assertThat(store.items.getFirst().reasonCodes()).contains("SECTION_NOT_FOUND");
    assertThat(store.items.getFirst().claimedSectionKey()).isEqualTo(KEY_OTHER);
  }

  @Test
  void shouldNeverRunTheLoopTwice() {
    CitationVerifier verifier = verifier();
    FindingVerifier findingVerifier = new FindingVerifier(verifier);
    AnalysisFixtures.StubSource source = new AnalysisFixtures.StubSource(q -> List.of(KEY_OTHER));
    CorrectiveLoop loop =
        new CorrectiveLoop(
            source,
            new CandidateRetriever(source, AnalysisFixtures.sections()),
            findingVerifier,
            verifier,
            VerifyProperties.defaults());
    CandidateSet candidates = new CandidateSet(40);
    ModelSession session = session();
    List<Finding> afterLoop =
        loop.correct(
                session,
                AnalysisRequest.of(Side.WORKER),
                List.of(plantedFinding().uncertain(List.of("SECTION_NOT_FOUND"))),
                List.of(),
                candidates,
                context())
            .findings();

    // A second pass would be attempt 3; the verification context refuses it.
    assertThat(afterLoop.getFirst().verification()).isEqualTo(FindingVerification.UNCERTAIN);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> context().forAttempt(3, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldVerifyTheRepairedCitationWhenTheAgentFindsARealSupportingSection() {
    CitationVerifier verifier = verifier();
    FindingVerifier findingVerifier = new FindingVerifier(verifier);
    AnalysisFixtures.StubSource source = new AnalysisFixtures.StubSource(q -> List.of(KEY_NOTICE));
    CorrectiveLoop loop =
        new CorrectiveLoop(
            source,
            new CandidateRetriever(source, AnalysisFixtures.sections()),
            findingVerifier,
            verifier,
            VerifyProperties.defaults());
    ModelSession session =
        AnalysisFixtures.session(
            AnalysisFixtures.gateway(
                request ->
                    TestGateways.json(
                        "{\"status\":\"OK\",\"reason\":\"NONE\",\"citations\":[{\"section_key\":\""
                            + KEY_NOTICE
                            + "\",\"quote\":\""
                            + VerifyFixtures.QUOTE_NOTICE
                            + "\",\"claim\":\""
                            + VerifyFixtures.CLAIM_NOTICE
                            + "\"}]}"),
                log));

    Finding repaired =
        loop.correct(
                session,
                AnalysisRequest.of(Side.WORKER),
                List.of(plantedFinding().uncertain(List.of("SECTION_NOT_FOUND"))),
                List.of(),
                new CandidateSet(40),
                context())
            .findings()
            .getFirst();

    assertThat(repaired.verification()).isEqualTo(FindingVerification.VERIFIED);
    assertThat(repaired.citations())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.sectionKey()).isEqualTo(KEY_NOTICE);
              assertThat(c.verification().attempt()).isEqualTo(2);
            });
  }

  @Test
  void shouldTreatARepairOutsideTheCandidatesAsAbstain() {
    CitationVerifier verifier = verifier();
    AnalysisFixtures.StubSource source = new AnalysisFixtures.StubSource(q -> List.of(KEY_NOTICE));
    CorrectiveLoop loop =
        new CorrectiveLoop(
            source,
            new CandidateRetriever(source, AnalysisFixtures.sections()),
            new FindingVerifier(verifier),
            verifier,
            VerifyProperties.defaults());
    // The model insists on a key the schema forbids: schema-invalid after repair -> ABSTAIN.
    ModelSession session =
        AnalysisFixtures.session(
            AnalysisFixtures.gateway(
                request ->
                    TestGateways.json(
                        "{\"status\":\"OK\",\"reason\":\"NONE\",\"citations\":[{\"section_key\":\""
                            + FAKE_KEY
                            + "\",\"quote\":\""
                            + FAKE_QUOTE
                            + "\",\"claim\":\"invented\"}]}"),
                log));

    CorrectiveLoop.Round round =
        loop.correct(
            session,
            AnalysisRequest.of(Side.WORKER),
            List.of(plantedFinding().uncertain(List.of("SECTION_NOT_FOUND"))),
            List.of(),
            new CandidateSet(40),
            context());

    assertThat(round.corrections().getFirst().abstained()).isTrue();
    assertThat(round.findings().getFirst().verification()).isEqualTo(FindingVerification.UNCERTAIN);
  }

  @Test
  void shouldNeverLetAClaimBecomeACitationWithoutTheVerifier() {
    // The only way to a VerifiedCitation is CitationVerifier; a finding refuses an empty list.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> plantedFinding().verified(List.<VerifiedCitation>of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
