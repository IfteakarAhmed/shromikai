package bd.shromikai.analysis.live;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.testsupport.live.CappedChatPort;
import bd.shromikai.testsupport.live.LiveEnv;
import bd.shromikai.testsupport.live.LiveSpendLedger;
import bd.shromikai.testsupport.live.RecordingChatPort;
import java.nio.file.Path;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the live tests: the real application on the {@code dev} profile (real adapters, the dev
 * database with the ingested corpus, local Ollama) with the real cloud ports wrapped so that the
 * US$3 spend cap, a per-run call limit and, in record mode, fixture recording hold. Nothing here
 * runs without {@code -PliveOk=smoke} or {@code -PliveOk=record}.
 */
@ActiveProfiles("dev")
@Import(LiveBase.Wrapping.class)
abstract class LiveBase {

  /** Directory the recorder writes fixtures to. */
  static final Path FIXTURES = Path.of("src/test/resources/fixtures/llm");

  @DynamicPropertySource
  static void live(DynamicPropertyRegistry registry) {
    registry.add("shromikai.providers.anthropic.api-key", () -> LiveEnv.get("ANTHROPIC_API_KEY"));
    registry.add("shromikai.providers.gemini.api-key", () -> LiveEnv.get("GEMINI_API_KEY"));
    registry.add("shromikai.models.verifier", () -> LiveEnv.get("GEMINI_MODEL"));
    registry.add("shromikai.models.judge", () -> LiveEnv.get("GEMINI_MODEL"));
    registry.add("shromikai.live.kind", () -> System.getProperty("shromikai.live", ""));
    // Settings the application reads from the environment; the values come from ../.env.
    registry.add("shromikai.auth.jwt-secret", () -> LiveEnv.get("JWT_SECRET"));
    for (String[] pair :
        new String[][] {
          {"spring.datasource.url", "DB_URL"},
          {"spring.datasource.username", "DB_USER"},
          {"spring.datasource.password", "DB_PASSWORD"},
          {"shromikai.ollama.base-url", "OLLAMA_URL"},
          {"shromikai.pricing.gemini.input", "GEMINI_PRICE_IN"},
          {"shromikai.pricing.gemini.output", "GEMINI_PRICE_OUT"},
        }) {
      if (LiveEnv.has(pair[1])) {
        registry.add(pair[0], () -> LiveEnv.get(pair[1]));
      }
    }
  }

  /** What a live run is called in the ledger and where it records. */
  static final class Run {
    static volatile String purpose = "live";
    static volatile String caseId = "live";
    static volatile int maxCalls = 3;
    static volatile boolean record = false;

    /** At most this many live calls of a prompt in one run (for example risk-analysis: 1). */
    static volatile java.util.Map<String, Integer> perPromptLimits = java.util.Map.of();

    /** Ledger purpose prefix of the contract and its own cap (US$1.20 for the first recording). */
    static volatile String contractPrefix = "live";

    static volatile long contractCapMicros = Long.MAX_VALUE;

    /** The synthetic contract whose ground-truth entities stand in for the local PII model. */
    static volatile String localPiiStandInFor = null;

    private Run() {}
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class Wrapping implements BeanPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
      this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      if (!(bean instanceof ChatPort port) || bean instanceof FakeChatPort) {
        return bean;
      }
      ChatPort wrapped = port;
      if (port.provider().equals("ollama") && Run.localPiiStandInFor != null) {
        // The local qwen model on this machine (Docker, no GPU) needs about 30 s to load and then
        // runs at about 7 tokens a second, so pii-extract hits its 30 s timeout and the job fails
        // closed before any cloud call (as designed). For this recording only, the local step
        // answers with the contract's ground-truth entity list; redaction, the placeholder map and
        // the egress guard are the real ones.
        String contract = Run.localPiiStandInFor;
        return new ChatPort() {
          @Override
          public String provider() {
            return "ollama";
          }

          @Override
          public bd.shromikai.shared.ai.ChatResponse call(
              bd.shromikai.shared.ai.ChatRequest request) {
            return new bd.shromikai.shared.ai.ChatResponse(
                bd.shromikai.testsupport.AnalysisHarness.piiAnswer(
                    bd.shromikai.testsupport.AnalysisHarness.entitiesOf(contract)),
                "local-stand-in-ground-truth",
                bd.shromikai.shared.ai.TokenUsage.NONE,
                false);
          }
        };
      }
      boolean cloud = port.provider().equals("anthropic") || port.provider().equals("gemini");
      if (cloud) {
        PricingProperties pricing =
            Binder.get(environment).bind("shromikai", PricingProperties.class).get();
        wrapped =
            new CappedChatPort(
                wrapped,
                new LiveSpendLedger(),
                new CostCalculator(pricing),
                Run.purpose,
                Run.maxCalls,
                Run.perPromptLimits,
                Run.contractPrefix,
                Run.contractCapMicros);
      }
      if (Run.record) {
        // Outside the cap: a call already recorded is replayed and costs nothing.
        wrapped = new RecordingChatPort(wrapped, FIXTURES, Run.caseId, true);
      }
      return wrapped;
    }

    @Bean
    LiveSpendLedger liveSpendLedger() {
      return new LiveSpendLedger();
    }
  }
}
