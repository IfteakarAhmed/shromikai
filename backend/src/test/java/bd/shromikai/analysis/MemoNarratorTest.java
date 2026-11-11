package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-51, FR-56: narrative from the model, the reading level reaches the prompt, safe fallback. */
@Tag("unit")
@Requirement("FR-51")
@Requirement("FR-56")
class MemoNarratorTest {

  private static Finding finding() {
    return new Finding(
        "f1",
        SensitiveText.of("Short notice"),
        FindingKind.CONFLICT,
        Severity.HIGH,
        false,
        FindingSource.LLM_ANALYSIS,
        "TERMINATION",
        List.of("c1"),
        SensitiveText.of("The clause gives only seven days notice."),
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

  private static ChatPort answering(String json, List<ChatRequest> log) {
    return new ChatPort() {
      @Override
      public String provider() {
        return "fake";
      }

      @Override
      public ChatResponse call(ChatRequest request) {
        log.add(request);
        return new ChatResponse(
            TestGateways.json(json),
            "stub-" + request.modelId(),
            new TokenUsage(500, 100, 0, 0),
            true);
      }
    };
  }

  private static AnalysisRequest request(String language, Map<String, String> profile) {
    return new AnalysisRequest(Side.WORKER, language, profile);
  }

  @Test
  void usesTheModelsSummaryAndStepsAndPicksThePromptByLanguage() {
    List<ChatRequest> log = new java.util.concurrent.CopyOnWriteArrayList<>();
    var gateway =
        AnalysisFixtures.gatewayOver(
            answering(
                "{\"status\":\"OK\",\"reason\":\"NONE\",\"summary\":\"One risk found.\","
                    + "\"next_steps\":[\"Ask for a copy.\"]}",
                log));
    var session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    var narrative =
        new MemoNarrator().narrate(session, request("en", Map.of()), List.of(finding()));

    assertThat(narrative.fromModel()).isTrue();
    assertThat(narrative.summary().reveal()).isEqualTo("One risk found.");
    assertThat(log).extracting(ChatRequest::promptId).containsExactly("memo-en");
  }

  @Test
  void theReadingLevelOfTheProfileReachesThePrompt() {
    List<ChatRequest> log = new java.util.concurrent.CopyOnWriteArrayList<>();
    var gateway =
        AnalysisFixtures.gatewayOver(
            answering(
                "{\"status\":\"OK\",\"reason\":\"NONE\",\"summary\":\"x y.\",\"next_steps\":[]}",
                log));
    var session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    new MemoNarrator()
        .narrate(session, request("bn", Map.of("reading_level", "BASIC")), List.of(finding()));

    assertThat(log.get(0).user().reveal()).contains("reading_level: BASIC");
    assertThat(log.get(0).promptId()).isEqualTo("memo-bn");
  }

  @Test
  void anInvalidAnswerGivesTheFixedNarrativeWithoutAModel() {
    List<ChatRequest> log = new java.util.concurrent.CopyOnWriteArrayList<>();
    var gateway = AnalysisFixtures.gatewayOver(answering("{\"nonsense\":true}", log));
    var session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    var narrative =
        new MemoNarrator().narrate(session, request("bn", Map.of()), List.of(finding()));

    assertThat(narrative.fromModel()).isFalse();
    assertThat(narrative.summary().reveal()).contains("টি ঝুঁকি");
    assertThat(narrative.nextSteps()).isNotEmpty();
  }

  @Test
  void anEgressBlockStillStopsTheAnalysis() {
    var port =
        new ChatPort() {
          @Override
          public String provider() {
            return "fake";
          }

          @Override
          public ChatResponse call(ChatRequest request) {
            throw new EgressBlockedException("blocked");
          }
        };
    var session =
        AnalysisFixtures.session(AnalysisFixtures.gatewayOver(port), EgressContext.NONE, 1.0);

    assertThatThrownBy(
            () -> new MemoNarrator().narrate(session, request("en", Map.of()), List.of(finding())))
        .isInstanceOf(EgressBlockedException.class);
  }

  /**
   * R-01: the narrative is the one memo text no verifier sees, so a statute reference in it (§,
   * "section N", "ধারা N", Latin or Bangla digits) is refused and the fixed narrative is used.
   */
  @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "Notice is required by section 9 of the Act.",
        "Notice is required by Section ৯ of the Act.",
        "See § 9 for the notice rule.",
        "ধারা ৯ অনুযায়ী নোটিস লাগবে।",
        "ধারা 9 অনুযায়ী নোটিস লাগবে।",
        "ধারা৯ অনুযায়ী নোটিস লাগবে।"
      })
  void refusesAStatuteReferenceInTheSummaryAndFallsBack(String hostileText) {
    for (String where : List.of("summary", "step")) {
      List<ChatRequest> log = new java.util.concurrent.CopyOnWriteArrayList<>();
      String summary = where.equals("summary") ? hostileText : "One risk found.";
      String step = where.equals("step") ? hostileText : "Ask for a copy.";
      var gateway =
          AnalysisFixtures.gatewayOver(
              answering(
                  "{\"status\":\"OK\",\"reason\":\"NONE\",\"summary\":\""
                      + summary
                      + "\",\"next_steps\":[\""
                      + step
                      + "\"]}",
                  log));
      var session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

      var narrative =
          new MemoNarrator().narrate(session, request("en", Map.of()), List.of(finding()));

      assertThat(narrative.fromModel()).as(where).isFalse();
      assertThat(narrative.summary().reveal()).as(where).doesNotContain(hostileText);
      assertThat(narrative.nextSteps()).as(where).isNotEmpty();
    }
  }

  @Test
  void keepsAnAnswerThatOnlyUsesPlainNumbersAndWords() {
    List<ChatRequest> log = new java.util.concurrent.CopyOnWriteArrayList<>();
    var gateway =
        AnalysisFixtures.gatewayOver(
            answering(
                "{\"status\":\"OK\",\"reason\":\"NONE\",\"summary\":\"2 risks found in"
                    + " this section of the review.\",\"next_steps\":[\"Ask for a copy within 7"
                    + " days.\"]}",
                log));
    var session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    var narrative =
        new MemoNarrator().narrate(session, request("en", Map.of()), List.of(finding()));

    assertThat(narrative.fromModel()).isTrue();
  }
}
