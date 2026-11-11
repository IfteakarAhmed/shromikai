package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.analysis.CandidateSet.Candidate;
import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifyFixtures;
import bd.shromikai.verify.VerifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-46, ADR-0017: a repair call sees at most {@value CorrectiveLoop#MAX_REPAIR_CANDIDATES}
 * candidate sections: the ones the new search found, then the ones retrieved for the finding's
 * clauses, then the best of the rest; it may cite only those, and attempt 2 accepts only those. The
 * first live run sent all 40 (about 48,000 input tokens per call).
 */
@Tag("unit")
@Requirement("FR-46")
class CorrectiveLoopCandidatesTest {

  private final List<ChatRequest> log = new CopyOnWriteArrayList<>();

  private static Candidate candidate(String key) {
    return new Candidate(
        key,
        "XV-LAB",
        "Fictional act",
        key,
        null,
        "en",
        "Fictional text of " + key + ".",
        null,
        false);
  }

  private static String key(int i) {
    return "XV-LAB:" + i + "@original";
  }

  /** 40 analysis-level sections; the clause c4 was matched to sections 30 to 34. */
  private static CandidateSet fortySections() {
    CandidateSet set = new CandidateSet(40);
    for (int i = 1; i <= 40; i++) {
      set.add(candidate(key(i)));
    }
    set.assign("c4", List.of(key(30), key(31), key(32), key(33), key(34)));
    return set;
  }

  private Finding finding() {
    return new Finding(
        "f1",
        SensitiveText.of("Hours above the limit"),
        FindingKind.CONFLICT,
        Severity.HIGH,
        false,
        FindingSource.LLM_ANALYSIS,
        "WORKING_HOURS",
        List.of("c4"),
        SensitiveText.of("Twelve hours a day."),
        SensitiveText.of(""),
        0.8,
        null,
        List.of(),
        FindingVerification.UNCERTAIN,
        List.of(),
        List.of("SECTION_NOT_FOUND"),
        null,
        null);
  }

  private CorrectiveLoop loop(AnalysisFixtures.StubSource source) {
    var models = VerifyFixtures.models(null);
    CitationVerifier verifier =
        VerifyFixtures.verifier(
            new VerifyFixtures.Corpus(),
            VerifyFixtures.gateway(
                models,
                new VerifyFixtures.SupportPort(
                    "anthropic", r -> VerifyFixtures.verdict("SUPPORTS", 0.9))),
            models);
    SectionSource sections =
        key ->
            Optional.of(
                VerifyFixtures.section(
                    key, "XV-LAB", "1", "original", true, null, "Fictional text."));
    return new CorrectiveLoop(
        source,
        new CandidateRetriever(source, sections),
        new FindingVerifier(verifier),
        verifier,
        VerifyProperties.defaults());
  }

  @Test
  void shouldShowAtMostTwelveSections_newOnesFirst_thenTheClausesOwn() {
    List<String> added = List.of("XV-NEW:1@original", "XV-NEW:2@original", "XV-NEW:3@original");
    CandidateSet roomy = new CandidateSet(50);
    fortySections().all().forEach(roomy::add);
    added.forEach(k -> roomy.add(candidate(k)));
    roomy.assign("c4", List.of(key(30), key(31), key(32), key(33), key(34)));

    List<Candidate> shown = CorrectiveLoop.repairCandidates(finding(), roomy, added);

    assertThat(shown).hasSize(CorrectiveLoop.MAX_REPAIR_CANDIDATES).hasSize(12);
    assertThat(shown.subList(0, 3))
        .extracting(Candidate::sectionKey)
        .containsExactlyElementsOf(added);
    assertThat(shown.subList(3, 8))
        .extracting(Candidate::sectionKey)
        .containsExactly(key(30), key(31), key(32), key(33), key(34));
    assertThat(shown.subList(8, 12))
        .extracting(Candidate::sectionKey)
        .as("filled from the front of the analysis-level set")
        .containsExactly(key(1), key(2), key(3), key(4));
    assertThat(shown).extracting(Candidate::sectionKey).doesNotHaveDuplicates();
  }

  @Test
  void shouldSendTwelveSectionsAndRestrictTheCitationEnumToThem() {
    AnalysisFixtures.StubSource source =
        new AnalysisFixtures.StubSource(q -> List.of("XV-NEW:1@original", key(30)));
    CandidateSet set = fortySections();
    // The new section does not fit the full set; use room so it joins.
    CandidateSet roomy = new CandidateSet(60);
    set.all().forEach(roomy::add);
    roomy.assign("c4", set.keysOf("c4"));
    ModelSession session =
        AnalysisFixtures.session(
            AnalysisFixtures.gateway(
                r ->
                    TestGateways.json(
                        "{\"status\":\"ABSTAIN\",\"reason\":\"NO_SUPPORTING_SECTION\",\"citations\":[]}"),
                log));

    CorrectiveLoop.Round round =
        loop(source)
            .correct(
                session,
                AnalysisRequest.of(Side.WORKER),
                List.of(finding()),
                List.of(AnalysisFixtures.clause("c4", ClauseType.WORKING_HOURS, "Twelve hours.")),
                roomy,
                new VerificationContext(VerifyFixtures.TODAY, Set.of("XV"), "r", roomy.keys(), 1));

    ChatRequest sent = log.getFirst();
    JsonNode enumValues =
        sent.outputSchema().at("/properties/citations/items/properties/section_key/enum");
    assertThat(enumValues).hasSize(12);
    assertThat(sent.user().reveal().split("\"sectionKey\"", -1).length - 1).isEqualTo(12);
    assertThat(sent.user().reveal()).contains("XV-NEW:1@original");
    assertThat(round.corrections().getFirst().abstained()).isTrue();
  }

  @Test
  void shouldTreatACitationOutsideTheTwelveAsAbstain_evenIfItIsAmongTheFortyOfTheAnalysis() {
    AnalysisFixtures.StubSource source = new AnalysisFixtures.StubSource(q -> List.of());
    CandidateSet set = fortySections();
    ModelSession session =
        AnalysisFixtures.session(
            AnalysisFixtures.gateway(
                r ->
                    TestGateways.json(
                        "{\"status\":\"OK\",\"reason\":\"NONE\",\"citations\":[{\"section_key\":\""
                            + key(40)
                            + "\",\"quote\":\"Fictional text of x\",\"claim\":\"A claim.\"}]}"),
                log));

    CorrectiveLoop.Round round =
        loop(source)
            .correct(
                session,
                AnalysisRequest.of(Side.WORKER),
                List.of(finding()),
                List.of(AnalysisFixtures.clause("c4", ClauseType.WORKING_HOURS, "Twelve hours.")),
                set,
                new VerificationContext(VerifyFixtures.TODAY, Set.of("XV"), "r", set.keys(), 1));

    // key(40) is a real candidate of the analysis but the agent was not shown it: the schema
    // refuses it, the one repair repeats it, and the finding stays UNCERTAIN.
    assertThat(round.corrections().getFirst().abstained()).isTrue();
    assertThat(round.findings().getFirst().verification()).isEqualTo(FindingVerification.UNCERTAIN);
    assertThat(round.findings().getFirst().citations()).isEmpty();
  }

  @Test
  void shouldShrinkTheRepairInputFarBelowTheWholeSetOfFortySections() {
    CandidateSet set = fortySections();
    List<Candidate> shown = CorrectiveLoop.repairCandidates(finding(), set, List.of());

    int whole = set.all().stream().mapToInt(c -> c.authoritativeText().length()).sum();
    int sent = shown.stream().mapToInt(c -> c.authoritativeText().length()).sum();
    assertThat(shown).hasSize(12);
    assertThat(sent).isLessThanOrEqualTo(whole * 12 / 40 + 1);
  }

  @Test
  void shouldKeepAFindingWithoutClausesWorking_usingTheNewAndTheBestSections() {
    Finding omission = finding();
    omission =
        new Finding(
            "f2",
            omission.title(),
            FindingKind.OMISSION,
            Severity.MEDIUM,
            true,
            FindingSource.LLM_ANALYSIS,
            "MATERNITY_BENEFIT",
            List.of(),
            omission.explanation(),
            omission.whyItMatters(),
            0.7,
            null,
            List.of(),
            FindingVerification.UNCERTAIN,
            List.of(),
            List.of(),
            null,
            null);

    List<Candidate> shown =
        CorrectiveLoop.repairCandidates(omission, fortySections(), List.of(key(7)));

    assertThat(shown).hasSize(12);
    assertThat(shown.getFirst().sectionKey()).isEqualTo(key(7));
  }
}
