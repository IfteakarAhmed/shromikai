package bd.shromikai.tooling;

import bd.shromikai.analysis.AnalysisRequest;
import bd.shromikai.analysis.CandidateRetriever;
import bd.shromikai.analysis.CandidateSet;
import bd.shromikai.analysis.CandidateSource;
import bd.shromikai.analysis.Clause;
import bd.shromikai.analysis.ClauseType;
import bd.shromikai.analysis.CorrectiveLoop;
import bd.shromikai.analysis.Finding;
import bd.shromikai.analysis.FindingKind;
import bd.shromikai.analysis.FindingSource;
import bd.shromikai.analysis.FindingVerification;
import bd.shromikai.analysis.FindingVerifier;
import bd.shromikai.analysis.ModelSession;
import bd.shromikai.analysis.ReviewQueue;
import bd.shromikai.analysis.SectionSource;
import bd.shromikai.analysis.Side;
import bd.shromikai.law.LawReadService.SectionLookup;
import bd.shromikai.law.Section;
import bd.shromikai.retrieval.Retrieval;
import bd.shromikai.retrieval.RetrievalOrigin;
import bd.shromikai.retrieval.RetrievalResult;
import bd.shromikai.rules.Severity;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.PromptRegistry;
import bd.shromikai.shared.ai.PromptRenderer;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.CitationVerifier;
import bd.shromikai.verify.ClaimedCitation;
import bd.shromikai.verify.ReviewItem;
import bd.shromikai.verify.ReviewItemFactory;
import bd.shromikai.verify.ReviewItemStore;
import bd.shromikai.verify.Verdict;
import bd.shromikai.verify.VerificationContext;
import bd.shromikai.verify.VerifyProperties;
import bd.shromikai.verify.internal.SectionLookupPort;
import bd.shromikai.verify.internal.SupportVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * {@code scripts/demo-fake-citation.sh}: shows, step by step, the verifier catching a planted fake
 * statute section, then the corrective loop and the review item (FR-46). The statute text comes
 * from the corpus in the local database; the models are <strong>scripted stand-ins</strong> (no
 * cloud call, nothing is spent) and the output says so. This is a demonstration of the pipeline's
 * behaviour, not a measurement of any model (R-07, R-12).
 */
public final class DemoFakeCitation {

  private static final String REAL_KEY = "BLA-2006:100@original";

  private DemoFakeCitation() {}

  public static void main(String[] args) {
    String url = env("DB_URL", "jdbc:postgresql://localhost:5432/shromikai");
    JdbcClient jdbc =
        JdbcClient.create(
            new DriverManagerDataSource(
                url, env("DB_USER", "shromikai"), env("DB_PASSWORD", "shromikai")));
    Demo demo = new Demo(jdbc);
    demo.run();
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  // ------------------------------------------------------------------------------------------

  private static final class Demo implements SectionLookupPort, SectionSource {

    private final JdbcClient jdbc;

    Demo(JdbcClient jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public Optional<SectionLookup> find(String key) {
      return jdbc.sql(
              """
              select s.section_key, a.code, a.title_bn, a.title_en, s.section_no, s.heading_bn,
                     s.heading_en, a.authoritative_lang, s.text_bn, s.text_en, s.version_label,
                     s.effective_from, s.is_current, s.source_url, a.jurisdiction, a.status
                from law_section s join law_act a on a.id = s.act_id
               where s.section_key = :key
              """)
          .param("key", key)
          .query(
              (rs, n) -> {
                boolean bn = "bn".equals(rs.getString("authoritative_lang"));
                String text = bn ? rs.getString("text_bn") : rs.getString("text_en");
                Date from = rs.getDate("effective_from");
                Section section =
                    new Section(
                        rs.getString("section_key"),
                        rs.getString("code"),
                        rs.getString("title_bn"),
                        rs.getString("title_en"),
                        rs.getString("section_no"),
                        null,
                        rs.getString("heading_bn"),
                        rs.getString("heading_en"),
                        rs.getString("authoritative_lang"),
                        text == null ? "" : text,
                        null,
                        rs.getString("version_label"),
                        from == null ? null : from.toLocalDate(),
                        rs.getBoolean("is_current"),
                        rs.getString("source_url"),
                        java.time.Instant.EPOCH,
                        null,
                        List.of(),
                        "demo");
                return new SectionLookup(
                    section, rs.getString("jurisdiction"), rs.getString("status"));
              })
          .optional();
    }

    @Override
    public Optional<Section> current(String sectionKey) {
      return find(sectionKey).map(SectionLookup::section).filter(Section::isCurrent);
    }

    // ----------------------------------------------------------------------------------------

    void run() {
      Section real =
          find(REAL_KEY)
              .map(SectionLookup::section)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Section "
                              + REAL_KEY
                              + " is not in the local corpus: run ./gradlew ingestLaws"));
      String realQuote = firstSentence(real.authoritativeText());
      say("ShromikAI: the verifier catches a planted fake statute section");
      say("Models here are scripted stand-ins (no cloud call, nothing spent). The statute text is");
      say(
          "the ingested corpus in the local database. This shows behaviour, not a model's quality.");
      rule();
      say("Real section in the corpus: " + REAL_KEY + " (" + real.headingBn() + ")");
      say("  stored text: " + shorten(real.authoritativeText(), 110));
      say("  source:      " + real.sourceUrl());

      List<ChatRequest> modelCalls = new ArrayList<>();
      ModelGateway gateway = gateway(modelCalls, realQuote);
      CitationVerifier verifier =
          new CitationVerifier(
              this,
              new SupportVerifier(gateway, models()),
              VerifyProperties.defaults(),
              Clock.systemUTC());
      VerificationContext context =
          new VerificationContext(LocalDate.now(), Set.of("BD"), "demo", Set.of(REAL_KEY), 1);

      step(1, "A generic model cites a section that does not exist: BLA-2006:999@original");
      show(
          verifier.verify(
              claim(
                  "BLA-2006:999@original",
                  "প্রত্যেক শ্রমিক এক বৎসরের চাকুরীর পর একটি কোম্পানির গাড়ি পাইবার অধিকারী হইবেন",
                  "A worker is owed a company car."),
              context));

      step(2, "It cites a real section but invents the quote");
      show(
          verifier.verify(
              claim(
                  REAL_KEY,
                  "শ্রমিক প্রতি সপ্তাহে একটি করে অতিরিক্ত বেতন পাইবেন বলিয়া গণ্য হইবে",
                  "A worker gets extra pay each week."),
              context));

      step(3, "It cites a real section with a real quote, but the section was not retrieved");
      show(
          verifier.verify(
              claim(REAL_KEY, realQuote, "The working hours section bears on this clause."),
              new VerificationContext(
                  LocalDate.now(), Set.of("BD"), "demo", Set.of("BLA-2006:101@amend-2013"), 1)));

      step(4, "Real section, real quote, a claim the section does not support (independent check)");
      show(
          verifier.verify(
              claim(REAL_KEY, realQuote, "The section gives every worker a company car."),
              context));

      step(5, "Real section, real quote, supported claim");
      Verdict ok =
          verifier.verify(
              claim(
                  REAL_KEY, realQuote, "The section on working hours limits ordinary daily hours."),
              context);
      show(ok);
      if (ok.citation() != null) {
        say("  shown to the user (stored statute text): " + shorten(ok.citation().quote(), 100));
        say("  source: " + ok.citation().sourceUrl());
      }

      step(
          6,
          "The corrective loop: the planted fake becomes a finding; it is rejected, repaired once");
      FindingVerifier findingVerifier = new FindingVerifier(verifier);
      CandidateSource source = new RealSource(realKeyResult(real));
      CandidateRetriever retriever = new CandidateRetriever(source, this);
      CorrectiveLoop loop =
          new CorrectiveLoop(
              source, retriever, findingVerifier, verifier, VerifyProperties.defaults());
      ModelSession session =
          new ModelSession(gateway, cost(), EgressContext.NONE, 1_000_000, 1_000_000);
      Finding planted = plantedFinding("BLA-2006:999@original", realQuote);
      FindingVerifier.Round first = findingVerifier.verify(List.of(planted), context);
      say(
          "  attempt 1: "
              + first.verdicts().getFirst().status()
              + " "
              + first.verdicts().getFirst().reasonCodes()
              + " -> finding is "
              + first.findings().getFirst().verification()
              + ", no citation shown");
      CandidateSet candidates = new CandidateSet(40);
      CorrectiveLoop.Round round =
          loop.correct(
              session,
              AnalysisRequest.of(Side.WORKER),
              first.findings(),
              List.of(
                  new Clause(
                      "c1",
                      "1.",
                      List.of("p1"),
                      SensitiveText.of("Daily hours are 12."),
                      Set.of(),
                      ClauseType.WORKING_HOURS,
                      0.9)),
              candidates,
              context);
      Finding repaired = round.findings().getFirst();
      say(
          "  retrieval ran again (+"
              + round.corrections().getFirst().newSections()
              + " section), the agent was asked once");
      say(
          "  attempt 2: finding is "
              + repaired.verification()
              + (repaired.citations().isEmpty()
                  ? ""
                  : ", citation "
                      + repaired.citations().getFirst().sectionKey()
                      + " (attempt "
                      + repaired.citations().getFirst().verification().attempt()
                      + ")"));

      step(7, "Same finding when the agent cannot find a supporting section: it stays uncertain");
      ModelSession abstaining =
          new ModelSession(abstainingGateway(), cost(), EgressContext.NONE, 1_000_000, 1_000_000);
      CorrectiveLoop.Round second =
          loop.correct(
              abstaining,
              AnalysisRequest.of(Side.WORKER),
              first.findings(),
              List.of(),
              new CandidateSet(40),
              context);
      Finding uncertain = second.findings().getFirst();
      List<ReviewItem> saved = new ArrayList<>();
      ReviewItemStore store = saved::add;
      ReviewQueue queue = new ReviewQueue(new ReviewItemFactory(Clock.systemUTC()), store);
      queue.open(
          UUID.randomUUID(),
          Side.WORKER,
          List.of(uncertain),
          List.of(),
          Map.of(),
          Map.of(),
          Set.of(REAL_KEY));
      say(
          "  finding: "
              + uncertain.verification()
              + " (shown as অনিশ্চিত / uncertain, no citation)");
      say(
          "  review items opened: "
              + saved.size()
              + (saved.isEmpty()
                  ? ""
                  : " reasons " + saved.getFirst().reasonCodes() + " (sent for human review)"));
      rule();
      say("Result: the fake section never reached a user. Only VERIFIED citations are shown.");
    }

    private static RetrievalResult realKeyResult(Section real) {
      return new RetrievalResult(
          real.sectionKey(),
          real.actCode(),
          real.sectionNo(),
          real.headingBn(),
          real.headingEn(),
          "",
          real.sourceUrl(),
          1,
          1,
          0.5,
          RetrievalOrigin.SEARCH);
    }

    private static final class RealSource implements CandidateSource {
      private final RetrievalResult result;

      RealSource(RetrievalResult result) {
        this.result = result;
      }

      @Override
      public Retrieval search(String query, String clauseType, EgressContext egress) {
        return new Retrieval(List.of(result), false, true);
      }
    }

    private Finding plantedFinding(String fakeKey, String unusedQuote) {
      return new Finding(
          "f1",
          SensitiveText.of("Daily working hours above the limit"),
          FindingKind.CONFLICT,
          Severity.HIGH,
          false,
          FindingSource.LLM_ANALYSIS,
          "WORKING_HOURS",
          List.of("c1"),
          SensitiveText.of("The clause sets twelve ordinary hours a day."),
          SensitiveText.of("A worker may be overworked."),
          0.8,
          null,
          List.of(
              ClaimedCitation.llm(
                  fakeKey,
                  "প্রত্যেক শ্রমিক এক বৎসরের চাকুরীর পর একটি কোম্পানির গাড়ি পাইবার অধিকারী হইবেন",
                  SensitiveText.of("A worker is owed a company car after one year."),
                  "en")),
          FindingVerification.UNCERTAIN,
          List.of(),
          List.of(),
          null,
          null);
    }

    private static ClaimedCitation claim(String key, String quote, String claim) {
      return ClaimedCitation.llm(key, quote, SensitiveText.of(claim), "en");
    }

    private static void show(Verdict verdict) {
      var r = verdict.result();
      var c = r.checks();
      say("  (a) existence: " + c.existence().outcome());
      say(
          "  (b) quote:     "
              + c.quoteMatch().outcome()
              + (c.quoteMatch().similarity() == null
                  ? ""
                  : " (similarity " + String.format("%.2f", c.quoteMatch().similarity()) + ")"));
      say(
          "  (c) support:   "
              + c.support().outcome()
              + (c.support().label() == null
                  ? ""
                  : " " + c.support().label() + " (scripted stand-in verifier)"));
      say("  => " + r.status() + (r.reasonCodes().isEmpty() ? "" : "  " + r.reasonCodes()));
    }

    private static String firstSentence(String text) {
      String stripped = text.replaceFirst("^[০-৯0-9]+\\s*[।.]\\s*", "").strip();
      int end = stripped.indexOf("ঃ");
      String sentence = end > 20 ? stripped.substring(0, end) : stripped;
      return shorten(sentence, 200);
    }

    private static String shorten(String text, int max) {
      String one = text.replaceAll("\\s+", " ").strip();
      return one.length() <= max ? one : one.substring(0, max) + "...";
    }

    private static void step(int n, String text) {
      say("");
      say("Step " + n + ": " + text);
    }

    private static void rule() {
      say("------------------------------------------------------------------------------");
    }

    private static void say(String line) {
      System.out.println(line); // a demo tool: its output is the product (not main code)
    }

    // ---- scripted stand-ins -------------------------------------------------------------------

    private static ShromikaiProperties.Models models() {
      return new ShromikaiProperties.Models(
          "claude-sonnet-5-5", "claude-haiku-5-5", null, "claude-haiku-5-5", null, "q", "b");
    }

    private static CostCalculator cost() {
      return new CostCalculator(new PricingProperties(Map.of()));
    }

    private ModelGateway gateway(List<ChatRequest> log, String realQuote) {
      ChatPort scripted =
          new ChatPort() {
            @Override
            public String provider() {
              return "fake";
            }

            @Override
            public ChatResponse call(ChatRequest request) {
              log.add(request);
              JsonNode out;
              if (request.promptId().startsWith("verify-support")) {
                String user = request.user().reveal();
                boolean hours = user.contains("working hours") && user.contains("ঘণ্টা");
                out =
                    TestGateways.json(
                        "{\"label\":\""
                            + (hours ? "SUPPORTS" : "UNRELATED")
                            + "\",\"confidence\":0.9,\"reason\":\"scripted\"}");
              } else if (request.promptId().equals("risk-citation-repair")) {
                String key = REAL_KEY;
                out =
                    TestGateways.json(
                        "{\"status\":\"OK\",\"reason\":\"NONE\",\"citations\":[{\"section_key\":\""
                            + key
                            + "\",\"quote\":"
                            + TestGateways.MAPPER.valueToTree(realQuote)
                            + ",\"claim\":\"The section on working hours limits ordinary daily hours.\"}]}");
              } else {
                throw new IllegalStateException("No scripted answer for " + request.promptId());
              }
              return new ChatResponse(out, "scripted-stand-in", new TokenUsage(0, 0, 0, 0), true);
            }
          };
      return new ModelGateway(
          new PromptRegistry(TestGateways.MAPPER),
          new PromptRenderer(TestGateways.MAPPER),
          List.of(scripted),
          models());
    }

    private ModelGateway abstainingGateway() {
      ChatPort scripted =
          new ChatPort() {
            @Override
            public String provider() {
              return "fake";
            }

            @Override
            public ChatResponse call(ChatRequest request) {
              return new ChatResponse(
                  TestGateways.json(
                      "{\"status\":\"ABSTAIN\",\"reason\":\"NO_SUPPORTING_SECTION\",\"citations\":[]}"),
                  "scripted-stand-in",
                  new TokenUsage(0, 0, 0, 0),
                  true);
            }
          };
      return new ModelGateway(
          new PromptRegistry(TestGateways.MAPPER),
          new PromptRenderer(TestGateways.MAPPER),
          List.of(scripted),
          models());
    }
  }
}
