package bd.shromikai.verify;

import bd.shromikai.law.LawReadService.SectionLookup;
import bd.shromikai.law.Section;
import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.internal.SectionLookupPort;
import bd.shromikai.verify.internal.SupportVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Fictional acts and sections for verifier tests (pack xx, R-10). Nothing here is law. */
public final class VerifyFixtures {

  public static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
  public static final String KEY_NOTICE = "XV-LAB:9@original";
  public static final String KEY_OTHER = "XV-LAB:12@original";
  public static final String NOTICE_TEXT =
      "An employer shall give a worker written notice of dismissal at least fourteen days before"
          + " the dismissal takes effect. This rule does not apply during the first month.";
  public static final String OTHER_TEXT =
      "Overtime shall be paid at a rate fixed by agreement between the worker and the employer.";
  public static final String QUOTE_NOTICE =
      "shall give a worker written notice of dismissal at least fourteen days";
  public static final String CLAIM_NOTICE =
      "The employer must give the worker written notice before dismissal.";

  private VerifyFixtures() {}

  public static Section section(
      String key,
      String act,
      String no,
      String version,
      boolean current,
      LocalDate effectiveFrom,
      String text) {
    return new Section(
        key,
        act,
        null,
        "Fictional Labour Act (XV)",
        no,
        null,
        null,
        "Heading " + no,
        "en",
        text,
        null,
        version,
        effectiveFrom,
        current,
        "https://example.test/xv/" + no,
        Instant.parse("2026-11-01T00:00:00Z"),
        "hash",
        List.of(),
        "2026.11.01-1");
  }

  /** A corpus of fictional sections addressed by key. */
  public static final class Corpus implements SectionLookupPort {
    private final Map<String, SectionLookup> byKey = new HashMap<>();

    public Corpus() {
      add(
          section(KEY_NOTICE, "XV-LAB", "9", "original", true, null, NOTICE_TEXT),
          "XV",
          "IN_FORCE");
      add(section(KEY_OTHER, "XV-LAB", "12", "original", true, null, OTHER_TEXT), "XV", "IN_FORCE");
    }

    public Corpus add(Section section, String jurisdiction, String actStatus) {
      byKey.put(section.sectionKey(), new SectionLookup(section, jurisdiction, actStatus));
      return this;
    }

    @Override
    public Optional<SectionLookup> find(String sectionKey) {
      return Optional.ofNullable(byKey.get(sectionKey));
    }
  }

  public static VerificationContext context(String... candidates) {
    return new VerificationContext(TODAY, Set.of("XV"), "2026.11.01-1", Set.of(candidates), 1);
  }

  public static ClaimedCitation llmClaim(String key, String quote, String claim) {
    return ClaimedCitation.llm(key, quote, SensitiveText.of(claim), "en");
  }

  /** A chat port that answers every support request with the function's JSON and counts calls. */
  public static final class SupportPort implements ChatPort {
    public final List<ChatRequest> calls = new ArrayList<>();
    private final String provider;
    private final Function<ChatRequest, JsonNode> answer;

    public SupportPort(String provider, Function<ChatRequest, JsonNode> answer) {
      this.provider = provider;
      this.answer = answer;
    }

    @Override
    public String provider() {
      return provider;
    }

    @Override
    public ChatResponse call(ChatRequest request) {
      calls.add(request);
      return new ChatResponse(answer.apply(request), "stub-" + provider, TokenUsage.NONE, false);
    }
  }

  public static JsonNode verdict(String label, double confidence) {
    return TestGateways.json(
        "{\"label\":\"" + label + "\",\"confidence\":" + confidence + ",\"reason\":\"stub\"}");
  }

  public static ShromikaiProperties.Models models(String verifier) {
    return new ShromikaiProperties.Models(
        "claude-sonnet-5-5",
        "claude-haiku-5-5",
        verifier,
        "claude-haiku-5-5",
        null,
        "qwen2.5:7b-instruct",
        "bge-m3");
  }

  public static CitationVerifier verifier(
      SectionLookupPort corpus, ModelGateway gateway, ShromikaiProperties.Models models) {
    return new CitationVerifier(
        corpus,
        new SupportVerifier(gateway, models),
        VerifyProperties.defaults(),
        Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), java.time.ZoneOffset.UTC));
  }

  public static ModelGateway gateway(ShromikaiProperties.Models models, ChatPort... ports) {
    return new ModelGateway(
        new bd.shromikai.shared.ai.PromptRegistry(TestGateways.MAPPER),
        new bd.shromikai.shared.ai.PromptRenderer(TestGateways.MAPPER),
        List.of(ports),
        models);
  }
}
