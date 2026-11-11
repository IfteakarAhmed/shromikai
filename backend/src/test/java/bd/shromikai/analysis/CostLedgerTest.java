package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-62: token and cost totals per stage and model match the recorded calls, cache included. */
@Tag("unit")
@Requirement("FR-62")
class CostLedgerTest {

  private final List<TokenUsage> served = new ArrayList<>();

  private ModelGateway gatewayServing(TokenUsage... usages) {
    int[] next = {0};
    ChatPort port =
        new ChatPort() {
          @Override
          public String provider() {
            return "fake";
          }

          @Override
          public ChatResponse call(ChatRequest request) {
            TokenUsage usage = usages[next[0]++ % usages.length];
            served.add(usage);
            return new ChatResponse(
                TestGateways.json(
                    "{\"status\":\"OK\",\"reason\":\"NONE\",\"document_type\":\"EMPLOYMENT_CONTRACT\",\"clauses\":[]}"),
                "stub-" + request.modelId(),
                usage,
                true);
          }
        };
    return AnalysisFixtures.gatewayOver(port);
  }

  @Test
  void theLedgerSumsTokensCacheTokensAndCostOfEveryCall() {
    ModelGateway gateway =
        gatewayServing(new TokenUsage(1000, 100, 0, 4000), new TokenUsage(500, 50, 4000, 0));
    ModelSession session = AnalysisFixtures.session(gateway, EgressContext.NONE, 1.0);

    session.call(Stage.RISK, "clause-classify", java.util.Map.of("clauses", java.util.List.of()));
    session.call(Stage.RISK, "clause-classify", java.util.Map.of("clauses", java.util.List.of()));

    ModelSession.StageUsage risk = session.usage(Stage.RISK);
    assertThat(risk.calls()).isEqualTo(2);
    assertThat(risk.cacheWriteTokens()).isEqualTo(4000);
    assertThat(risk.cacheReadTokens()).isEqualTo(4000);
    assertThat(risk.tokensOut()).isEqualTo(150);
    assertThat(session.cacheReadTokens()).isEqualTo(4000);
    assertThat(session.cacheWriteTokens()).isEqualTo(4000);
    long expected =
        served.stream()
            .mapToLong(u -> AnalysisFixtures.cost().usdMicros("stub-claude-haiku-5-5", u))
            .sum();
    assertThat(session.costUsdMicros()).isEqualTo(expected);
    assertThat(risk.costMicros()).isEqualTo(expected);
  }
}
