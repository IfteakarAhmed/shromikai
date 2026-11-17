package bd.shromikai.eval.harness;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.testsupport.AnalysisHarness;
import bd.shromikai.testsupport.live.LiveSpendLedger;
import bd.shromikai.testsupport.live.RecordingChatPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * How the evaluation reaches the models (P10). Every cloud port is wrapped, outermost first:
 *
 * <ol>
 *   <li>{@link CloudCapture}: counts each request and scans it for the contract's PII (M-16);
 *   <li>a read-only replay of the demo recordings, for {@code syn-en-02} only;
 *   <li>{@link RecordingChatPort} on the separate eval fixture folder: replays a request already
 *       recorded there, otherwise records the live answer (record-missing only, never overwrites);
 *   <li>{@link SpendCap}: refuses a live call that could pass the ledger cap (US$3.30), the eval
 *       cap (US$1.45 over every {@code eval:} purpose) or the current step's own cap.
 * </ol>
 *
 * The local PII model keeps its production code path; only for the evaluation its per-call timeout
 * is raised (the Mac runs it in Docker without a GPU), and production stays fail-closed at 30 s. A
 * contract whose local step took over 15 minutes uses its ground-truth entity list instead, and the
 * report says so.
 */
@TestConfiguration
public class EvalWrapping implements BeanPostProcessor, EnvironmentAware {

  /** The eval recordings: separate from the demo recordings, which are only read. */
  public static final Path EVAL_FIXTURES = Path.of("src/test/resources/fixtures/llm-eval");

  public static final Path DEMO_FIXTURES = Path.of("src/test/resources/fixtures/llm");

  /** Per-call timeout of the local PII model in evaluation runs only. */
  public static final Duration LOCAL_TIMEOUT = Duration.ofMinutes(15);

  /** Mutable run state, set by the runner before each step. */
  public static final class Run {
    public static volatile String purpose = "eval:unset";
    public static volatile String caseId = "eval-unset";

    /** The demo case to replay read-only, or null. */
    public static volatile @Nullable String demoCase = null;

    /** Contracts whose local PII step uses the ground-truth stand-in. */
    public static volatile Set<String> piiStandIn = Set.of();

    public static volatile @Nullable String currentContract = null;

    /** Live spend this step may add, in micro-dollars. */
    public static volatile long stepCapMicros = 0;

    public static volatile long stepStartMicros = 0;
    public static volatile int maxLiveCalls = 0;
    public static volatile boolean liveAllowed = false;

    /**
     * Messages of the calls this wrapper refused (prompt ID and amounts only, never contract text),
     * so the runner can write them to the run notes.
     */
    public static final java.util.List<String> refusals =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    private Run() {}
  }

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (!(bean instanceof ChatPort port) || bean instanceof FakeChatPort) {
      return bean;
    }
    if (port.provider().equals("ollama")) {
      return new LocalPort(port);
    }
    if (!port.provider().equals("anthropic") && !port.provider().equals("gemini")) {
      return bean;
    }
    PricingProperties pricing =
        Binder.get(environment).bind("shromikai", PricingProperties.class).get();
    CostCalculator cost = new CostCalculator(pricing);
    ChatPort capped = new SpendCap(port, new LiveSpendLedger(), cost);
    ChatPort recorded = new CaseRecorder(capped);
    ChatPort demo = new DemoReplay(recorded);
    return new Capture(demo, cost);
  }

  // ------------------------------------------------------------------ the wrappers

  /** Local PII model: longer timeout in eval only, or the ground-truth stand-in. */
  static final class LocalPort implements ChatPort {
    private final ChatPort delegate;

    LocalPort(ChatPort delegate) {
      this.delegate = delegate;
    }

    @Override
    public String provider() {
      return "ollama";
    }

    @Override
    public ChatResponse call(ChatRequest r) {
      String contract = Run.currentContract;
      if (contract != null && Run.piiStandIn.contains(contract)) {
        return new ChatResponse(
            AnalysisHarness.piiAnswer(AnalysisHarness.entitiesOf(contract)),
            "local-stand-in-ground-truth",
            TokenUsage.NONE,
            false);
      }
      ChatRequest longer =
          new ChatRequest(
              r.promptId(),
              r.promptVersion(),
              r.role(),
              r.modelId(),
              r.system(),
              r.user(),
              r.outputSchema(),
              r.maxOutputTokens(),
              LOCAL_TIMEOUT,
              r.temperature(),
              r.effort(),
              r.egressContext());
      return delegate.call(longer);
    }
  }

  /** Scans and counts every cloud-bound request. */
  static final class Capture implements ChatPort {
    private final ChatPort delegate;
    private final CostCalculator cost;

    Capture(ChatPort delegate, CostCalculator cost) {
      this.delegate = delegate;
      this.cost = cost;
    }

    @Override
    public String provider() {
      return delegate.provider();
    }

    @Override
    public ChatResponse call(ChatRequest request) {
      ChatResponse response = delegate.call(request);
      CloudCapture.record(
          request, response.recorded(), cost.usdMicros(response.modelId(), response.usage()));
      return response;
    }
  }

  /** Replays the demo's recording of the same request (read only), for the case named. */
  static final class DemoReplay implements ChatPort {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ChatPort delegate;

    DemoReplay(ChatPort delegate) {
      this.delegate = delegate;
    }

    @Override
    public String provider() {
      return delegate.provider();
    }

    @Override
    public ChatResponse call(ChatRequest request) {
      String demo = Run.demoCase;
      if (demo != null) {
        Path file =
            DEMO_FIXTURES
                .resolve(request.promptId())
                .resolve("v" + request.promptVersion())
                .resolve(demo)
                .resolve(request.renderedSha256().substring(0, 16) + ".json");
        if (Files.isRegularFile(file)) {
          try {
            var fixture = MAPPER.readTree(file.toFile());
            var usage = fixture.path("usage");
            return new ChatResponse(
                fixture.get("response"),
                fixture.path("modelId").asText(),
                new TokenUsage(
                    usage.path("inputTokens").asLong(),
                    usage.path("outputTokens").asLong(),
                    usage.path("cacheReadTokens").asLong(),
                    0),
                true,
                fixture.hasNonNull("stopReason") ? fixture.get("stopReason").asText() : null);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
      return delegate.call(request);
    }
  }

  /** Record-missing in the eval folder, under the current case. */
  static final class CaseRecorder implements ChatPort {
    private final ChatPort delegate;

    CaseRecorder(ChatPort delegate) {
      this.delegate = delegate;
    }

    @Override
    public String provider() {
      return delegate.provider();
    }

    @Override
    public ChatResponse call(ChatRequest request) {
      return new RecordingChatPort(delegate, EVAL_FIXTURES, Run.caseId, true).call(request);
    }
  }

  /**
   * The spend caps. A call is refused before it is made when its worst case (input at two
   * characters per token plus the prompt's output cap), added to what is spent and to the worst
   * case of calls still in flight, could pass any cap. It never retries.
   */
  static final class SpendCap implements ChatPort {
    private final ChatPort delegate;
    private final LiveSpendLedger ledger;
    private final CostCalculator cost;
    private long inFlight;
    private int calls;

    SpendCap(ChatPort delegate, LiveSpendLedger ledger, CostCalculator cost) {
      this.delegate = delegate;
      this.ledger = ledger;
      this.cost = cost;
    }

    @Override
    public String provider() {
      return delegate.provider();
    }

    private static LiveSpendLedger.SpendCapException refused(String message) {
      Run.refusals.add(message);
      return new LiveSpendLedger.SpendCapException(message);
    }

    @Override
    public ChatResponse call(ChatRequest request) {
      long worst =
          cost.worstCaseUsdMicros(
              request.modelId(),
              (request.system().length() + request.user().reveal().length()) / 2 + 200,
              request.maxOutputTokens());
      synchronized (this) {
        if (!Run.liveAllowed) {
          throw refused(
              "A live call of "
                  + request.promptId()
                  + " was needed but live calls are off for this step (-PliveOk=eval)");
        }
        if (++calls > Run.maxLiveCalls) {
          throw refused("More than " + Run.maxLiveCalls + " live calls in this step; stopping");
        }
        long spent = ledger.spentMicros();
        long evalSpent = ledger.spentOn(LiveSpendLedger.EVAL_PREFIX);
        long stepSpent = evalSpent - Run.stepStartMicros;
        if (spent + inFlight + worst > ledger.capMicros()
            || evalSpent + inFlight + worst > LiveSpendLedger.EVAL_CAP_MICROS
            || stepSpent + inFlight + worst > Run.stepCapMicros) {
          throw refused(
              String.format(
                  java.util.Locale.ROOT,
                  "Refused %s (model %s, max output %d tokens; over: %s): worst case US$%.4f;"
                      + " ledger US$%.4f of %.2f, eval US$%.4f of %.2f,"
                      + " this step US$%.4f of %.4f, in flight US$%.4f",
                  request.promptId(),
                  request.modelId(),
                  request.maxOutputTokens(),
                  (spent + inFlight + worst > ledger.capMicros() ? "ledger " : "")
                      + (evalSpent + inFlight + worst > LiveSpendLedger.EVAL_CAP_MICROS
                          ? "eval "
                          : "")
                      + (stepSpent + inFlight + worst > Run.stepCapMicros ? "step" : ""),
                  worst / 1e6,
                  spent / 1e6,
                  ledger.capMicros() / 1e6,
                  evalSpent / 1e6,
                  LiveSpendLedger.EVAL_CAP_MICROS / 1e6,
                  stepSpent / 1e6,
                  Run.stepCapMicros / 1e6,
                  inFlight / 1e6));
        }
        inFlight += worst;
      }
      ChatResponse response;
      try {
        response = delegate.call(request);
      } catch (bd.shromikai.shared.ai.ModelOutputInvalidException e) {
        TokenUsage u = e.usage();
        ledger.record(
            Run.purpose + ":" + request.promptId(),
            request.modelId(),
            u.totalInputTokens(),
            u.outputTokens(),
            cost.usdMicros(request.modelId(), u));
        throw e;
      } finally {
        synchronized (this) {
          inFlight -= worst;
        }
      }
      TokenUsage u = response.usage();
      ledger.record(
          Run.purpose + ":" + request.promptId(),
          response.modelId(),
          u.totalInputTokens(),
          u.outputTokens(),
          cost.usdMicros(response.modelId(), u));
      return response;
    }
  }
}
