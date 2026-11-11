package bd.shromikai.testsupport.live;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.ModelOutputInvalidException;
import bd.shromikai.shared.ai.TokenUsage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps a real cloud {@link ChatPort} so that no live call is made without the cumulative cap and a
 * per-run call limit holding (ADR-0015, rule "never loop live calls in a test"). Before a call it
 * estimates the worst case (input tokens at two characters per token, which over-counts Bangla less
 * than it should, plus the prompt's output cap) and refuses if that could pass the cap. After the
 * call it records the reported usage. It does not retry: the adapters already retry twice.
 */
public final class CappedChatPort implements ChatPort {

  private final ChatPort delegate;
  private final LiveSpendLedger ledger;
  private final CostCalculator cost;
  private final String purpose;
  private final int maxCalls;
  private final AtomicInteger calls = new AtomicInteger();
  private final Map<String, Integer> perPromptLimits;
  private final Map<String, AtomicInteger> perPrompt = new ConcurrentHashMap<>();
  private final String contractPrefix;
  private final long contractCapMicros;

  public CappedChatPort(
      ChatPort delegate,
      LiveSpendLedger ledger,
      CostCalculator cost,
      String purpose,
      int maxCalls) {
    this(delegate, ledger, cost, purpose, maxCalls, Map.of(), purpose, Long.MAX_VALUE);
  }

  /**
   * @param perPromptLimits at most this many live calls of a prompt in one run, for example one
   *     call of {@code risk-analysis} (a gateway repair would be a second call and is refused)
   * @param contractPrefix the ledger purpose whose total is capped by {@code contractCapMicros}
   */
  public CappedChatPort(
      ChatPort delegate,
      LiveSpendLedger ledger,
      CostCalculator cost,
      String purpose,
      int maxCalls,
      Map<String, Integer> perPromptLimits,
      String contractPrefix,
      long contractCapMicros) {
    this.delegate = delegate;
    this.ledger = ledger;
    this.cost = cost;
    this.purpose = purpose;
    this.maxCalls = maxCalls;
    this.perPromptLimits = perPromptLimits;
    this.contractPrefix = contractPrefix;
    this.contractCapMicros = contractCapMicros;
  }

  @Override
  public String provider() {
    return delegate.provider();
  }

  public int calls() {
    return calls.get();
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    if (calls.incrementAndGet() > maxCalls) {
      throw new LiveSpendLedger.SpendCapException(
          "More than " + maxCalls + " live calls in one run for " + purpose + "; stopping.");
    }
    Integer promptLimit = perPromptLimits.get(request.promptId());
    if (promptLimit != null
        && perPrompt.computeIfAbsent(request.promptId(), k -> new AtomicInteger()).incrementAndGet()
            > promptLimit) {
      throw new LiveSpendLedger.SpendCapException(
          "More than "
              + promptLimit
              + " live call(s) of "
              + request.promptId()
              + " allowed in this run; stopping (no retry).");
    }
    long inputEstimate = (request.system().length() + request.user().reveal().length()) / 2 + 200;
    long worst =
        cost.worstCaseUsdMicros(request.modelId(), inputEstimate, request.maxOutputTokens());
    ledger.reserve(
        request.promptId() + " (" + request.modelId() + ")",
        worst,
        contractPrefix,
        contractCapMicros);
    try {
      ChatResponse response = delegate.call(request);
      TokenUsage u = response.usage();
      ledger.record(
          purpose + ":" + request.promptId(),
          response.modelId(),
          u.totalInputTokens(),
          u.outputTokens(),
          cost.usdMicros(response.modelId(), u));
      return response;
    } catch (ModelOutputInvalidException e) {
      TokenUsage u = e.usage();
      ledger.record(
          purpose + ":" + request.promptId(),
          request.modelId(),
          u.totalInputTokens(),
          u.outputTokens(),
          cost.usdMicros(request.modelId(), u));
      throw e;
    }
  }
}
