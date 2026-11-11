package bd.shromikai.analysis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bd.shromikai.law.LawReadService;
import bd.shromikai.rules.Rule;
import bd.shromikai.rules.RuleEngine;
import bd.shromikai.rules.RuleSet;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.trace.AnalysisTraceStore;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ReviewItemFactory;
import bd.shromikai.verify.VerifyFixtures;
import bd.shromikai.verify.VerifyProperties;
import bd.shromikai.verify.internal.SupportVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The P6 pipeline wired by hand over stand-in model answers, a fictional corpus and an in-memory
 * review store (no Spring, no database, no cloud). Pack xx, R-10: nothing here is law.
 */
final class PipelineHarness {

  static final String CLAUSE_1 = "The employer may end the contract on 7 days notice.";
  static final String CLAUSE_2 = "Salary is paid monthly.";

  final List<ChatRequest> log = new CopyOnWriteArrayList<>();
  final AnalysisFixtures.StubSource source;
  final AnalysisFixtures.MemoryReviewStore reviewStore = new AnalysisFixtures.MemoryReviewStore();
  final AnalysisRunStore runs = mock(AnalysisRunStore.class);
  final AnalysisTraceStore traces = mock(AnalysisTraceStore.class);
  final JobStore jobs = new JobStore(Clock.systemUTC());
  final AnalysisPipeline pipeline;

  /** Answers a prompt; {@code null} falls through to the default answer. */
  interface Answers {
    JsonNode answer(ChatRequest request);
  }

  PipelineHarness(
      Answers overrides,
      AnalysisFixtures.StubSource source,
      List<Rule> activeRules,
      AnalysisProperties properties) {
    this.source = source;
    Function<ChatRequest, JsonNode> answers =
        request -> {
          JsonNode custom = overrides == null ? null : overrides.answer(request);
          return custom != null ? custom : defaultAnswer(request);
        };
    ModelGateway gateway = AnalysisFixtures.gateway(answers, log);
    var models = AnalysisFixtures.models();
    CitationVerifier citationVerifier =
        new CitationVerifier(
            new VerifyFixtures.Corpus(),
            new SupportVerifier(gateway, models),
            VerifyProperties.defaults(),
            Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));
    FindingVerifier findingVerifier = new FindingVerifier(citationVerifier);
    CandidateRetriever retriever = new CandidateRetriever(source, AnalysisFixtures.sections());
    CorrectiveLoop loop =
        new CorrectiveLoop(
            source, retriever, findingVerifier, citationVerifier, VerifyProperties.defaults());
    FactExtractor facts = new FactExtractor();
    RuleEngine engine = new RuleEngine();
    LawReadService law = mock(LawReadService.class);
    when(law.corpusRelease()).thenReturn("2026.11.01-1");
    pipeline =
        new AnalysisPipeline(
            facts,
            engine,
            new RuleSet(activeRules, List.of()),
            retriever,
            new RiskAgent(),
            new FindingMerge(),
            findingVerifier,
            loop,
            new ReviewQueue(new ReviewItemFactory(Clock.systemUTC()), reviewStore),
            new CounterOfferAgent(facts, engine),
            gateway,
            AnalysisFixtures.cost(),
            law,
            runs,
            traces,
            properties,
            Clock.systemUTC());
  }

  static AnalysisProperties properties() {
    return new AnalysisProperties(
        new AnalysisProperties.Analysis(
            Duration.ofSeconds(30),
            Map.of(
                "retrieve", Duration.ofSeconds(10),
                "rules", Duration.ofSeconds(10),
                "risk", Duration.ofSeconds(10),
                "verify", Duration.ofSeconds(10),
                "correct", Duration.ofSeconds(10),
                "compose", Duration.ofSeconds(10)),
            java.util.Set.of("XV")),
        new AnalysisProperties.Budget(1.0, 1_000_000));
  }

  static PipelineHarness standard() {
    return new PipelineHarness(
        null,
        new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
        List.of(AnalysisFixtures.noticeMinRule()),
        properties());
  }

  /** A job that has finished CLASSIFY with two clauses (the P5 steps are tested elsewhere). */
  AnalysisJob classifiedJob() {
    AnalysisJob job = jobs.create();
    job.intake("TEXT", 1, "en");
    job.classified(
        List.of(
            AnalysisFixtures.clause("c1", ClauseType.NOTICE_AND_RESIGNATION, CLAUSE_1),
            AnalysisFixtures.clause("c2", ClauseType.WAGES, CLAUSE_2)));
    return job;
  }

  AnalysisJob run() {
    AnalysisJob job = classifiedJob();
    pipeline.run(job, AnalysisRequest.of(Side.WORKER), AnalysisSubject.dev());
    return job;
  }

  long calls(String promptId) {
    return log.stream().filter(r -> r.promptId().equals(promptId)).count();
  }

  private static final Pattern DAYS = Pattern.compile("(\\d+) days");

  static JsonNode defaultAnswer(ChatRequest request) {
    return switch (request.promptId()) {
      case "clause-facts" -> facts(request);
      case "risk-analysis" ->
          TestGateways.json(
              """
              {"status":"OK","reason":"NONE","findings":[{"title":"Dismissal on short notice",
               "kind":"CONFLICT","severity":"HIGH","non_obvious":false,
               "clause_type":"NOTICE_AND_RESIGNATION","clause_ids":["c1"],
               "explanation":"The clause gives only seven days notice of dismissal.",
               "citations":[{"section_key":"XV-LAB:9@original",
                 "quote":"shall give a worker written notice of dismissal at least fourteen days",
                 "claim":"An employer must give a worker written notice before dismissal."}],
               "confidence":0.8}]}
              """);
      case "verify-support", "verify-support-claude" ->
          TestGateways.json("{\"label\":\"SUPPORTS\",\"confidence\":0.92,\"reason\":\"stand-in\"}");
      case "counter-offer" ->
          TestGateways.json(
              "{\"status\":\"OK\",\"reason\":\"NONE\",\"action\":\"REPLACE\",\"proposed_text\":"
                  + "\"The employer may end the contract on 14 days written notice.\","
                  + "\"rationale\":\"Gives the notice the section requires.\"}");
      case "risk-citation-repair" ->
          TestGateways.json(
              "{\"status\":\"ABSTAIN\",\"reason\":\"NO_SUPPORTING_SECTION\",\"citations\":[]}");
      default -> throw new IllegalStateException("No stand-in answer for " + request.promptId());
    };
  }

  /** Reads "N days" out of each clause the model is shown, as NOTICE_BY_EMPLOYER. */
  private static JsonNode facts(ChatRequest request) {
    String user = request.user().reveal();
    StringBuilder clauses = new StringBuilder();
    for (String id : List.of("c1", "c2", "proposal")) {
      if (user.contains("\"id\":\"" + id + "\"")) {
        String text = user.substring(user.indexOf("\"id\":\"" + id + "\""));
        text = text.substring(0, Math.min(text.length(), 300));
        Matcher m = DAYS.matcher(text);
        String fact =
            id.equals("c2") || !m.find()
                ? ""
                : "{\"parameter\":\"NOTICE_BY_EMPLOYER\",\"value\":"
                    + m.group(1)
                    + ",\"unit\":\"DAYS\",\"confidence\":0.9}";
        if (!clauses.isEmpty()) {
          clauses.append(',');
        }
        clauses.append("{\"id\":\"").append(id).append("\",\"facts\":[").append(fact).append("]}");
      }
    }
    return TestGateways.json(
        "{\"status\":\"OK\",\"reason\":\"NONE\",\"clauses\":[" + clauses + "]}");
  }
}
