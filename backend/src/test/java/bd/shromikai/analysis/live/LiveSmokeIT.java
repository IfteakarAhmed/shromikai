package bd.shromikai.analysis.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.live.LiveEnv;
import bd.shromikai.testsupport.live.LiveSpendLedger;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * FR-44, FR-47 (live): one tiny call per cloud provider, with small outputs, to prove the real
 * adapters accept the prompts' structured-output schemas: Claude through {@code
 * verify-support-claude} and Gemini through {@code verify-support}. The input is a fictional
 * statute sentence; nothing here is a contract. Runs only with {@code -PliveOk=smoke}; the US$3 cap
 * of the ledger holds and a run makes at most three calls.
 */
@Tag("live")
@Requirement("FR-44")
@SpringBootTest
class LiveSmokeIT extends LiveBase {

  @Autowired ModelGateway gateway;
  @Autowired LiveSpendLedger ledger;

  @BeforeAll
  static void optIn() {
    assumeTrue(LiveEnv.optedIn("smoke"), "live smoke test: run with -PliveOk=smoke");
    Run.purpose = "smoke";
    Run.caseId = "smoke";
    Run.maxCalls = 3;
    Run.record = false;
  }

  private static final Map<String, String> INPUT =
      Map.of(
          "claim", "An employer must give a worker written notice before dismissal.",
          "quote", "shall give a worker written notice of dismissal",
          "section_text",
              "Fictional section. An employer shall give a worker written notice of dismissal at"
                  + " least fourteen days before the dismissal takes effect.",
          "translation_text", "none");

  @Test
  void shouldGetAValidVerdictFromClaudeHaiku() {
    long before = ledger.spentMicros();

    ModelGateway.Result result = gateway.call("verify-support-claude", INPUT, EgressContext.NONE);

    assertThat(result.modelId()).startsWith("claude-");
    assertThat(result.output().path("label").asText())
        .isIn("SUPPORTS", "PARTIAL", "CONTRADICTS", "UNRELATED");
    assertThat(result.usage().outputTokens()).isPositive().isLessThanOrEqualTo(150);
    assertThat(ledger.spentMicros() - before)
        .as("a tiny call costs far less than a cent")
        .isBetween(1L, 10_000L);
  }

  @Test
  void shouldGetAValidVerdictFromGemini() {
    assumeTrue(
        LiveEnv.has("GEMINI_API_KEY") && LiveEnv.has("GEMINI_MODEL"), "no Gemini key or model");
    long before = ledger.spentMicros();

    ModelGateway.Result result = gateway.call("verify-support", INPUT, EgressContext.NONE);

    assertThat(result.modelId()).startsWith("gemini");
    assertThat(result.output().path("label").asText())
        .isIn("SUPPORTS", "PARTIAL", "CONTRADICTS", "UNRELATED");
    assertThat(ledger.spentMicros() - before).isBetween(1L, 10_000L);
  }
}
