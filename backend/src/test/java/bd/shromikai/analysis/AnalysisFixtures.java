package bd.shromikai.analysis;

import bd.shromikai.analysis.CandidateSet.Candidate;
import bd.shromikai.law.Section;
import bd.shromikai.privacy.PlaceholderMap;
import bd.shromikai.retrieval.Retrieval;
import bd.shromikai.retrieval.RetrievalOrigin;
import bd.shromikai.retrieval.RetrievalResult;
import bd.shromikai.rules.ClauseFact;
import bd.shromikai.rules.FactParameter;
import bd.shromikai.rules.Rule;
import bd.shromikai.rules.RuleComparator;
import bd.shromikai.rules.Unit;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.ReviewItem;
import bd.shromikai.verify.ReviewItemStore;
import bd.shromikai.verify.VerifyFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Fictional clauses, sections and ports for analysis tests (pack xx, R-10). Nothing is law. */
public final class AnalysisFixtures {

  public static final String NOTICE_TEXT = VerifyFixtures.NOTICE_TEXT;
  public static final String KEY_NOTICE = VerifyFixtures.KEY_NOTICE;
  public static final String KEY_OTHER = VerifyFixtures.KEY_OTHER;

  private AnalysisFixtures() {}

  public static Clause clause(String id, ClauseType type, String text) {
    return new Clause(
        id, id, List.of("p" + id.substring(1)), SensitiveText.of(text), Set.of(), type, 0.9);
  }

  public static ClauseFact fact(FactParameter p, double value, Unit unit) {
    return new ClauseFact(p, value, unit, 0.9, null);
  }

  /** A model gateway over one port answering every provider role (fake). */
  public static ModelGateway gateway(
      Function<ChatRequest, JsonNode> answer, List<ChatRequest> log) {
    ChatPort fake =
        new ChatPort() {
          @Override
          public String provider() {
            return "fake";
          }

          @Override
          public ChatResponse call(ChatRequest request) {
            log.add(request);
            return new ChatResponse(
                answer.apply(request),
                "stub-" + request.modelId(),
                new TokenUsage(1000, 100, 0, 0),
                true);
          }
        };
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            "gemini-test",
            "claude-haiku-5-5",
            null,
            "qwen2.5:7b-instruct",
            "bge-m3");
    return new ModelGateway(
        new bd.shromikai.shared.ai.PromptRegistry(TestGateways.MAPPER),
        new bd.shromikai.shared.ai.PromptRenderer(TestGateways.MAPPER),
        List.of(fake),
        models);
  }

  /** A model gateway over one hand-written port. */
  public static ModelGateway gatewayOver(ChatPort port) {
    return new ModelGateway(
        new bd.shromikai.shared.ai.PromptRegistry(TestGateways.MAPPER),
        new bd.shromikai.shared.ai.PromptRenderer(TestGateways.MAPPER),
        List.of(port),
        models());
  }

  public static ShromikaiProperties.Models models() {
    return new ShromikaiProperties.Models(
        "claude-sonnet-5-5",
        "claude-haiku-5-5",
        "gemini-test",
        "claude-haiku-5-5",
        null,
        "qwen2.5:7b-instruct",
        "bge-m3");
  }

  public static CostCalculator cost() {
    return new CostCalculator(
        new PricingProperties(
            Map.of(
                "claude-sonnet-5-5",
                new PricingProperties.Price(2.0, 10.0, 2.5, 4.0, 0.1, null),
                "claude-haiku-5-5",
                new PricingProperties.Price(0.1, 0.5, 0.125, 0.2, 0.01, null),
                "stub-claude-sonnet-5-5",
                new PricingProperties.Price(2.0, 10.0, 2.5, 4.0, 0.1, null),
                "stub-claude-haiku-5-5",
                new PricingProperties.Price(0.1, 0.5, 0.125, 0.2, 0.01, null),
                "stub-gemini-test",
                PricingProperties.Price.simple(0.3, 2.5))));
  }

  public static ModelSession session(ModelGateway gateway, EgressContext egress, double maxUsd) {
    return new ModelSession(gateway, cost(), egress, (long) (maxUsd * 1_000_000), 1_000_000);
  }

  public static ModelSession session(ModelGateway gateway) {
    return session(gateway, EgressContext.NONE, 1.0);
  }

  public static PlaceholderMap placeholders() {
    return new PlaceholderMap("test");
  }

  /** The fictional sections of the verify fixtures as a section source. */
  public static SectionSource sections() {
    VerifyFixtures.Corpus corpus = new VerifyFixtures.Corpus();
    return key -> corpus.find(key).map(l -> l.section()).filter(Section::isCurrent);
  }

  /** A candidate source returning the given section keys for every query, and counting queries. */
  public static final class StubSource implements CandidateSource {
    public final List<String> queries = new CopyOnWriteArrayList<>();
    private final Function<String, List<String>> keysFor;

    public StubSource(Function<String, List<String>> keysFor) {
      this.keysFor = keysFor;
    }

    @Override
    public Retrieval search(String redactedQuery, String clauseType, EgressContext egress) {
      queries.add(redactedQuery);
      List<RetrievalResult> results = new ArrayList<>();
      for (String key : keysFor.apply(redactedQuery)) {
        results.add(
            new RetrievalResult(
                key,
                "XV-LAB",
                "1",
                null,
                "Heading",
                "snippet",
                "https://example.test",
                1,
                1,
                0.5,
                RetrievalOrigin.SEARCH));
      }
      return new Retrieval(results, false, true);
    }
  }

  /** An in-memory review store. */
  public static final class MemoryReviewStore implements ReviewItemStore {
    public final List<ReviewItem> items = new CopyOnWriteArrayList<>();

    @Override
    public void save(ReviewItem item) {
      items.add(item);
    }
  }

  public static Optional<Candidate> candidateOf(String key) {
    return sections().current(key).map(CandidateRetriever::toCandidate);
  }

  /** An active, two-person-reviewed rule on the fictional notice section (xx, R-10). */
  public static Rule noticeMinRule() {
    return new Rule(
        "xx-notice-min",
        "NOTICE_AND_RESIGNATION",
        FactParameter.NOTICE_BY_EMPLOYER,
        RuleComparator.MIN,
        14.0,
        Unit.DAYS,
        null,
        new Rule.LegalBasis("XV-LAB", "9", VerifyFixtures.QUOTE_NOTICE),
        bd.shromikai.rules.Severity.HIGH,
        "কাল্পনিক নিয়ম: নোটিশ কমপক্ষে চৌদ্দ দিন (পরীক্ষার জন্য)।",
        "Fictional rule: notice must be at least fourteen days (for tests only).",
        "author@example.test",
        "reviewer@example.test",
        null,
        Rule.Status.ACTIVE,
        KEY_NOTICE);
  }
}
