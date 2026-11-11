package bd.shromikai.analysis;

import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The model calls of one analysis job: every call carries the job's egress context (R-03), is
 * counted against the job's token and cost budget (architecture §5.3, FR-48) and is added to the
 * usage of its stage for the trace. A call that would start after the budget is spent, or that
 * pushes it over, ends the job with {@code budget-exceeded}.
 */
public final class ModelSession {

  /** Tokens, cost and calls of one stage, for its trace event. */
  public static final class StageUsage {
    private long tokensIn;
    private long tokensOut;
    private long cacheRead;
    private long cacheWrite;
    private long costMicros;
    private int calls;
    private String promptId;
    private int promptVersion;
    private String modelId;

    synchronized void add(TokenUsage usage, long cost, String prompt, int version, String model) {
      tokensIn += usage.totalInputTokens();
      tokensOut += usage.outputTokens();
      cacheRead += usage.cacheReadTokens();
      cacheWrite += usage.cacheWriteTokens();
      costMicros += cost;
      calls++;
      promptId = prompt;
      promptVersion = version;
      modelId = model;
    }

    public synchronized long tokensIn() {
      return tokensIn;
    }

    public synchronized long tokensOut() {
      return tokensOut;
    }

    public synchronized long cacheReadTokens() {
      return cacheRead;
    }

    public synchronized long cacheWriteTokens() {
      return cacheWrite;
    }

    public synchronized long costMicros() {
      return costMicros;
    }

    public synchronized int calls() {
      return calls;
    }

    public synchronized String promptId() {
      return promptId;
    }

    public synchronized int promptVersion() {
      return promptVersion;
    }

    public synchronized String modelId() {
      return modelId;
    }
  }

  private final ModelGateway gateway;
  private final CostCalculator cost;
  private final EgressContext egress;
  private final long maxMicros;
  private final long maxTokens;
  private final AtomicLong micros = new AtomicLong();
  private final AtomicLong tokens = new AtomicLong();
  private final AtomicLong cacheRead = new AtomicLong();
  private final AtomicLong cacheWrite = new AtomicLong();
  private final Map<Stage, StageUsage> stages = new ConcurrentHashMap<>();

  public ModelSession(
      ModelGateway gateway,
      CostCalculator cost,
      EgressContext egress,
      long maxCostUsdMicros,
      long maxTokens) {
    this.gateway = gateway;
    this.cost = cost;
    this.egress = egress;
    this.maxMicros = maxCostUsdMicros;
    this.maxTokens = maxTokens;
  }

  public ModelGateway.Result call(Stage stage, String promptId, Map<String, ?> variables) {
    return call(stage, promptId, variables, Map.of());
  }

  public ModelGateway.Result call(
      Stage stage,
      String promptId,
      Map<String, ?> variables,
      Map<String, ? extends Collection<String>> enums) {
    ensureWithinBudget();
    ModelGateway.Result result;
    try {
      result = gateway.call(promptId, variables, egress, enums);
    } catch (ModelOutputInvalidException e) {
      charge(stage, gateway.modelIdFor(promptId), e.usage(), promptId, 0);
      throw e;
    }
    charge(stage, result.modelId(), result.usage(), result.promptId(), result.promptVersion());
    ensureWithinBudget();
    return result;
  }

  public EgressContext egress() {
    return egress;
  }

  public StageUsage usage(Stage stage) {
    return stages.computeIfAbsent(stage, s -> new StageUsage());
  }

  public long costUsdMicros() {
    return micros.get();
  }

  public long cacheReadTokens() {
    return cacheRead.get();
  }

  public long cacheWriteTokens() {
    return cacheWrite.get();
  }

  public long totalTokens() {
    return tokens.get();
  }

  public List<Stage> stagesWithUsage() {
    return List.copyOf(stages.keySet());
  }

  private void charge(Stage stage, String modelId, TokenUsage usage, String promptId, int version) {
    long cents = cost.usdMicros(modelId, usage);
    micros.addAndGet(cents);
    tokens.addAndGet(usage.totalInputTokens() + usage.outputTokens());
    cacheRead.addAndGet(usage.cacheReadTokens());
    cacheWrite.addAndGet(usage.cacheWriteTokens());
    usage(stage).add(usage, cents, promptId, version, modelId);
  }

  private void ensureWithinBudget() {
    if (micros.get() > maxMicros || tokens.get() > maxTokens) {
      throw new ProblemException(ProblemType.BUDGET_EXCEEDED);
    }
  }
}
