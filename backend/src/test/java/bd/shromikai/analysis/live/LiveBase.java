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
  }

  /** What a live run is called in the ledger and where it records. */
  static final class Run {
    static volatile String purpose = "live";
    static volatile String caseId = "live";
    static volatile int maxCalls = 3;
    static volatile boolean record = false;

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
      if (Run.record) {
        wrapped = new RecordingChatPort(wrapped, FIXTURES, Run.caseId);
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
                Run.maxCalls);
      }
      return wrapped;
    }

    @Bean
    LiveSpendLedger liveSpendLedger() {
      return new LiveSpendLedger();
    }
  }
}
