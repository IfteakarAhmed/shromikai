package bd.shromikai.analysis;

import static bd.shromikai.analysis.AnalysisFixtures.clause;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.rules.RuleEngine;
import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.VerifiedCitation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FR-41: a counter-offer that breaks rule {@code xx-notice-min} is regenerated once and dropped on
 * a second violation; an UNCERTAIN finding gets no counter-offer call. The model is a stand-in; the
 * rule engine, fact extraction and loop are the real classes.
 */
@Tag("it")
@Requirement("FR-41")
class CounterOfferRuleCheckIT {

  private final List<ChatRequest> log = new CopyOnWriteArrayList<>();
  private final CounterOfferAgent agent =
      new CounterOfferAgent(new FactExtractor(), new RuleEngine());

  private static final Pattern DAYS = Pattern.compile("(\\d+) days");

  /** Counter-offer proposals are served in order; clause-facts reads the days out of the text. */
  private ModelSession session(String... proposals) {
    AtomicInteger next = new AtomicInteger();
    return AnalysisFixtures.session(
        AnalysisFixtures.gateway(
            request -> {
              if (request.promptId().equals("counter-offer")) {
                String text = proposals[Math.min(next.getAndIncrement(), proposals.length - 1)];
                return TestGateways.json(
                    "{\"status\":\"OK\",\"reason\":\"NONE\",\"action\":\"REPLACE\",\"proposed_text\":\""
                        + text
                        + "\",\"rationale\":\"Gives notice and stays fair.\"}");
              }
              Matcher m = DAYS.matcher(request.user().reveal());
              String facts =
                  m.find()
                      ? "{\"parameter\":\"NOTICE_BY_EMPLOYER\",\"value\":"
                          + m.group(1)
                          + ",\"unit\":\"DAYS\",\"confidence\":0.9}"
                      : "";
              return TestGateways.json(
                  "{\"status\":\"OK\",\"reason\":\"NONE\",\"clauses\":[{\"id\":\"proposal\",\"facts\":["
                      + facts
                      + "]}]}");
            },
            log));
  }

  private Finding finding(FindingVerification verification, FindingKind kind) {
    Finding f =
        new Finding(
            "f1",
            SensitiveText.of("Dismissal without notice"),
            kind,
            Severity.HIGH,
            false,
            FindingSource.LLM_ANALYSIS,
            "OTHER",
            List.of("c4"),
            SensitiveText.of("The clause allows dismissal without notice."),
            SensitiveText.of("Pay is lost."),
            0.8,
            null,
            List.of(),
            FindingVerification.UNCERTAIN,
            List.of(),
            List.of(),
            null,
            null);
    if (verification == FindingVerification.VERIFIED) {
      return f.verified(List.of(verified()));
    }
    return f;
  }

  private static VerifiedCitation verified() {
    var verifier =
        bd.shromikai.verify.VerifyFixtures.verifier(
            new bd.shromikai.verify.VerifyFixtures.Corpus(),
            bd.shromikai.verify.VerifyFixtures.gateway(
                bd.shromikai.verify.VerifyFixtures.models(null),
                new bd.shromikai.verify.VerifyFixtures.SupportPort(
                    "anthropic", r -> bd.shromikai.verify.VerifyFixtures.verdict("SUPPORTS", 0.9))),
            bd.shromikai.verify.VerifyFixtures.models(null));
    return verifier
        .verify(
            bd.shromikai.verify.VerifyFixtures.llmClaim(
                AnalysisFixtures.KEY_NOTICE,
                bd.shromikai.verify.VerifyFixtures.QUOTE_NOTICE,
                bd.shromikai.verify.VerifyFixtures.CLAIM_NOTICE),
            bd.shromikai.verify.VerifyFixtures.context(AnalysisFixtures.KEY_NOTICE))
        .citation();
  }

  private Map<String, Clause> clauses() {
    return Map.of("c4", clause("c4", ClauseType.TERMINATION, "The employer may dismiss at once."));
  }

  private static final String BAD = "The employer gives the worker 7 days notice.";
  private static final String GOOD = "The employer gives the worker 14 days notice.";

  @Test
  void shouldRegenerateAProposalThatBreaksTheRuleAndAcceptTheSecond() {
    ModelSession session = session(BAD, GOOD);

    var results =
        agent.proposeAll(
            session,
            AnalysisRequest.of(Side.WORKER),
            List.of(finding(FindingVerification.VERIFIED, FindingKind.CONFLICT)),
            clauses(),
            List.of(AnalysisFixtures.noticeMinRule()));

    Redline redline = results.getFirst().outcome().redline();
    assertThat(redline).isNotNull();
    assertThat(redline.id()).isEqualTo("r1");
    assertThat(redline.attempts()).isEqualTo(2);
    assertThat(redline.proposedText().reveal()).isEqualTo(GOOD);
    assertThat(redline.ruleIds()).containsExactly("xx-notice-min");
    assertThat(calls("counter-offer")).isEqualTo(2);
    // The second request told the model what was wrong.
    assertThat(
            log.stream()
                .filter(r -> r.promptId().equals("counter-offer"))
                .skip(1)
                .findFirst()
                .orElseThrow()
                .user()
                .reveal())
        .contains("xx-notice-min");
  }

  @Test
  void shouldDropTheProposalAfterASecondViolation() {
    ModelSession session = session(BAD, BAD);

    var results =
        agent.proposeAll(
            session,
            AnalysisRequest.of(Side.WORKER),
            List.of(finding(FindingVerification.VERIFIED, FindingKind.CONFLICT)),
            clauses(),
            List.of(AnalysisFixtures.noticeMinRule()));

    assertThat(results.getFirst().outcome().redline()).isNull();
    assertThat(results.getFirst().outcome().droppedBecause()).isEqualTo("rule-violation");
    assertThat(calls("counter-offer")).as("one regeneration, then dropped").isEqualTo(2);
  }

  @Test
  void shouldMakeNoCounterOfferCallForAnUncertainFinding() {
    ModelSession session = session(GOOD);

    var results =
        agent.proposeAll(
            session,
            AnalysisRequest.of(Side.WORKER),
            List.of(finding(FindingVerification.UNCERTAIN, FindingKind.CONFLICT)),
            clauses(),
            List.of(AnalysisFixtures.noticeMinRule()));

    assertThat(results).isEmpty();
    assertThat(log).isEmpty();
  }

  @Test
  void shouldMakeNoCounterOfferForAnInteraction() {
    var results =
        agent.proposeAll(
            session(GOOD),
            AnalysisRequest.of(Side.WORKER),
            List.of(finding(FindingVerification.VERIFIED, FindingKind.INTERACTION)),
            clauses(),
            List.of(AnalysisFixtures.noticeMinRule()));

    assertThat(results).isEmpty();
  }

  @Test
  void shouldSkipFactExtractionWhenNoRuleCanBeViolated() {
    ModelSession session = session(BAD);

    var results =
        agent.proposeAll(
            session,
            AnalysisRequest.of(Side.WORKER),
            List.of(finding(FindingVerification.VERIFIED, FindingKind.CONFLICT)),
            clauses(),
            List.of());

    assertThat(results.getFirst().outcome().redline()).isNotNull();
    assertThat(calls("clause-facts")).isZero();
    assertThat(results.getFirst().outcome().redline().ruleIds()).isEmpty();
  }

  @Test
  void shouldNumberRedlinesInTheOrderOfTheFindings() {
    Finding second = finding(FindingVerification.VERIFIED, FindingKind.AMBIGUITY).withId("f2");
    Finding first = finding(FindingVerification.VERIFIED, FindingKind.CONFLICT);

    var results =
        agent.proposeAll(
            session(GOOD),
            AnalysisRequest.of(Side.EMPLOYER),
            List.of(first, second),
            clauses(),
            List.of(AnalysisFixtures.noticeMinRule()));

    assertThat(results).extracting(r -> r.outcome().redline().id()).containsExactly("r1", "r2");
    assertThat(results)
        .extracting(CounterOfferAgent.ForFinding::findingId)
        .containsExactly("f1", "f2");
  }

  private long calls(String promptId) {
    return log.stream().filter(r -> r.promptId().equals(promptId)).count();
  }
}
