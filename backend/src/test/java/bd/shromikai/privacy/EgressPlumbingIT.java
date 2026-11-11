package bd.shromikai.privacy;

import static bd.shromikai.testsupport.IdentityFixtures.bearer;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.analysis.AnalysisJob;
import bd.shromikai.auth.JwtService;
import bd.shromikai.auth.Role;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.shared.ai.anthropic.AnthropicChatAdapter;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.text.BanglaNormalizer;
import bd.shromikai.testsupport.AnalysisHarness;
import bd.shromikai.testsupport.AnthropicStub;
import bd.shromikai.testsupport.IntegrationTest;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FR-30, NFR-06: <strong>plumbing only</strong>. The full intake runs on all five synthetic
 * contracts with WireMock standing in for the cloud model, through the real Claude adapter and the
 * real egress guard, <em>with a local extractor that is handed the labelled entities</em>. It
 * proves that redaction, the placeholder map, the guard and the adapter are wired so that nothing
 * known to the job leaves the box, and that a law search with PII canaries makes zero cloud
 * requests (architecture §10.7).
 *
 * <p>It does <strong>not</strong> show that the real local model finds every name: a name the
 * extractor misses is not redacted (see {@link #shouldLeakANameTheLocalExtractorMissed}). That
 * recall is measured by M-16 with real Ollama, which is a known gap (docs/dev-log.md).
 */
@IntegrationTest
@Requirement("FR-30")
@Requirement("NFR-06")
class EgressPlumbingIT {

  @org.junit.jupiter.api.extension.RegisterExtension
  static WireMockExtension cloud =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .http2PlainDisabled(true)
                  .extensions(new AnthropicStub()))
          .build();

  @DynamicPropertySource
  static void cloudUrls(DynamicPropertyRegistry registry) {
    registry.add("shromikai.providers.anthropic.base-url", cloud::baseUrl);
    registry.add("shromikai.providers.gemini.base-url", cloud::baseUrl);
  }

  @Autowired TestRestTemplate http;
  @Autowired JwtService jwt;
  @Autowired FakeChatPort fakeChat;
  @Autowired EgressGuard springGuard;

  @BeforeEach
  void reset() {
    cloud.resetAll();
    cloud.stubFor(post(anyUrl()).willReturn(aResponse().withTransformers(AnthropicStub.NAME)));
    fakeChat.reset();
  }

  static List<String> contracts() {
    return SyntheticContracts.IDS;
  }

  private AnalysisHarness harnessWith(bd.shromikai.privacy.LocalEntities entities) {
    var guard =
        new PrivacyEgressGuard(
            new PiiDetector(),
            org.mockito.Mockito.mock(bd.shromikai.trace.AuditService.class),
            new SimpleMeterRegistry());
    var anthropic =
        new AnthropicChatAdapter(
            guard,
            new ShromikaiProperties.Provider("test-key", cloud.baseUrl()),
            TestGateways.MAPPER);
    return new AnalysisHarness(anthropic, AnalysisHarness.ollamaReturning(entities), image -> null);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldSendNoLabelledIdentifierToTheCloudModel(String id) {
    AnalysisHarness h = harnessWith(AnalysisHarness.entitiesOf(id));

    AnalysisJob job = h.service.analyseText(SyntheticContracts.text(id));

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.CLASSIFIED);
    List<LoggedRequest> requests = cloud.findAll(anyRequestedFor(anyUrl()));
    assertThat(requests).as("the cloud model was called").isNotEmpty();
    String all = String.join("\n", requests.stream().map(LoggedRequest::getBodyAsString).toList());
    String unescaped = all.replace("\\\"", "\"");
    for (SyntheticContracts.Entity entity : SyntheticContracts.entities(id)) {
      assertThat(unescaped).as(entity.type()).doesNotContain(entity.value());
      assertThat(BanglaNormalizer.toLatinDigits(unescaped))
          .as(entity.type() + " (Latin digits)")
          .doesNotContain(BanglaNormalizer.toLatinDigits(entity.value()));
    }
    assertThat(unescaped).contains("[PERSON_1]");
    assertThat(h.meters.find("shromikai_privacy_blocks_total").counter()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contracts")
  void shouldStillHideNidPhoneAndEmailWhenTheLocalModelFindsNothing(String id) {
    AnalysisHarness h = harnessWith(bd.shromikai.privacy.LocalEntities.NONE);

    AnalysisJob job = h.service.analyseText(SyntheticContracts.text(id));

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.CLASSIFIED);
    String all =
        String.join(
            "\n",
            cloud.findAll(anyRequestedFor(anyUrl())).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList());
    for (String value : SyntheticContracts.values(id, "NID", "PHONE", "EMAIL", "PASSPORT", "DOB")) {
      assertThat(BanglaNormalizer.toLatinDigits(all))
          .doesNotContain(BanglaNormalizer.toLatinDigits(value));
    }
  }

  /**
   * Negative case: when the extractor misses a person, no detector or guard stage can know the
   * name, so it reaches the cloud payload. This documents the limit of the plumbing; it is the
   * reason the real-model recall must be measured and not assumed.
   */
  @Test
  void shouldLeakANameTheLocalExtractorMissed() {
    String id = SyntheticContracts.IDS.getFirst();
    List<String> persons = SyntheticContracts.values(id, "PERSON");
    String missed = persons.getFirst();
    var found = AnalysisHarness.entitiesOf(id);
    // The extractor finds the organisations, addresses and IDs but no person at all.
    var withoutMissed =
        new LocalEntities(List.of(), found.organisations(), found.addresses(), found.employeeIds());
    AnalysisHarness h = harnessWith(withoutMissed);

    h.service.analyseText(SyntheticContracts.text(id));

    String all =
        String.join(
            "\n",
            cloud.findAll(anyRequestedFor(anyUrl())).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList());
    assertThat(all.replace("\\\"", "\"")).contains(missed);
  }

  @Test
  void shouldBlockAndFailTheAnalysisWhenAValueSlipsThroughRedaction() {
    // A guard that knows a value the redactor missed: the request must not leave the box.
    var guard =
        new PrivacyEgressGuard(
            new PiiDetector(),
            org.mockito.Mockito.mock(bd.shromikai.trace.AuditService.class),
            new SimpleMeterRegistry());
    PlaceholderMap map = new PlaceholderMap("a");
    map.placeholderFor(PiiType.PERSON, "Imran Sample Khan");
    var adapter =
        new AnthropicChatAdapter(
            guard,
            new ShromikaiProperties.Provider("test-key", cloud.baseUrl()),
            TestGateways.MAPPER);
    var request =
        new bd.shromikai.shared.ai.ChatRequest(
            "clause-classify",
            1,
            bd.shromikai.shared.ai.ModelRole.FAST,
            "claude-haiku-5-5",
            "s",
            bd.shromikai.shared.SensitiveText.of("signed by Imran Sample Khan"),
            TestGateways.MAPPER.createObjectNode(),
            10,
            java.time.Duration.ofSeconds(2),
            null,
            null,
            map);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.call(request))
        .isInstanceOf(bd.shromikai.shared.ai.EgressBlockedException.class);
    assertThat(cloud.findAll(anyRequestedFor(anyUrl()))).isEmpty();
  }

  @Test
  void shouldWireThePrivacyGuardAsTheEgressGuardOfTheApplication() {
    assertThat(springGuard).isInstanceOf(PrivacyEgressGuard.class);
  }

  @Test
  void shouldMakeZeroCloudRequestsForALawSearchWhoseQueryHoldsPiiCanaries() {
    String token = jwt.issueUser(UUID.randomUUID(), Role.WORKER, null, false).value();
    String query = "Abdul Karim 01712345678 NID 1234567890 karim@example.org overtime pay";

    ResponseEntity<JsonNode> response =
        http.exchange(
            "/api/v1/laws/search?q={q}&k=5",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class,
            query);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cloud.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    assertThat(fakeChat.calls()).as("no chat model call of any kind").isEmpty();
    List<String> seen = new ArrayList<>();
    fakeChat.calls().forEach(c -> seen.add(c.request().promptId()));
    assertThat(seen).isEmpty();
  }

  /**
   * MCP {@code verify_legal_citation} (FR-64, architecture §10.7): the claim is redacted locally by
   * {@code ClaimRedactionService}, then the support check goes through the real Claude adapter and
   * the real guard. WireMock sees placeholders and none of the canaries.
   */
  @Test
  void shouldSendAnMcpClaimToTheCloudModelOnlyAsPlaceholders() {
    var found = new LocalEntities(List.of("Abdul Karim Sample"), List.of(), List.of(), List.of());
    var guard =
        new PrivacyEgressGuard(
            new PiiDetector(),
            org.mockito.Mockito.mock(bd.shromikai.trace.AuditService.class),
            new SimpleMeterRegistry());
    var anthropic =
        new AnthropicChatAdapter(
            guard,
            new ShromikaiProperties.Provider("test-key", cloud.baseUrl()),
            TestGateways.MAPPER);
    var gateway = TestGateways.gateway(anthropic, AnalysisHarness.ollamaReturning(found));
    var models =
        new ShromikaiProperties.Models(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            null,
            "claude-haiku-5-5",
            null,
            "qwen2.5:7b-instruct",
            "bge-m3");
    var claims =
        new bd.shromikai.analysis.ClaimRedactionService(
            new PrivacyShield(
                new LocalEntityExtractor(gateway),
                new Redactor(new PiiDetector()),
                new InjectionDetector()));
    cloud.stubFor(
        post(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"msg_stub\",\"type\":\"message\",\"role\":\"assistant\","
                            + "\"model\":\"claude-haiku-5-5\",\"stop_reason\":\"end_turn\","
                            + "\"stop_sequence\":null,\"content\":[{\"type\":\"text\",\"text\":"
                            + "\"{\\\"label\\\":\\\"SUPPORTS\\\",\\\"confidence\\\":0.9,"
                            + "\\\"reason\\\":\\\"stand-in\\\"}\"}],"
                            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}")));
    String claim =
        "Abdul Karim Sample, phone 01712345678, NID 1234567890123, mail karim@example.org,"
            + " was dismissed without the notice the section requires.";

    try (var redacted = claims.redact(claim)) {
      var outcome =
          new bd.shromikai.verify.internal.SupportVerifier(gateway, models)
              .verify(
                  redacted.text(),
                  "Fictional section text about notice of dismissal.",
                  null,
                  "written notice of dismissal",
                  redacted.context(),
                  bd.shromikai.shared.text.OverlapChecker.of());
      assertThat(outcome).isInstanceOf(bd.shromikai.verify.internal.SupportVerifier.Judged.class);
    }

    String all =
        String.join(
            "\n",
            cloud.findAll(anyRequestedFor(anyUrl())).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList());
    assertThat(all).as("the cloud model was called").isNotEmpty();
    for (String canary :
        List.of("Abdul Karim", "01712345678", "1234567890123", "karim@example.org")) {
      assertThat(all.replace("\\\"", "\"")).as(canary).doesNotContain(canary);
    }
    assertThat(all).contains("[PERSON_1]");
  }
}
