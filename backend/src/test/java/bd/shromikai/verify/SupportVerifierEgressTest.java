package bd.shromikai.verify;

import static bd.shromikai.verify.VerifyFixtures.CLAIM_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.KEY_NOTICE;
import static bd.shromikai.verify.VerifyFixtures.QUOTE_NOTICE;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.ModelGateway;
import bd.shromikai.shared.ai.anthropic.AnthropicChatAdapter;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.text.OverlapChecker;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.verify.VerifyFixtures.Corpus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * FR-44, R-03: the model-written claim reaches the verifier only through the job's egress context,
 * and never when it copies the document.
 */
@Tag("unit")
@Requirement("FR-44")
@Requirement("FR-30")
class SupportVerifierEgressTest {

  private static final String SECRET = "Karim Uddin Chowdhury";

  @RegisterExtension
  static WireMockExtension anthropic =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private final List<@Nullable EgressContext> seen = new ArrayList<>();

  @BeforeEach
  void reset() {
    anthropic.resetAll();
    seen.clear();
    anthropic.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                okJson(
                    """
                    {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
                     "content":[{"type":"text","text":"{\\"label\\":\\"SUPPORTS\\",\\"confidence\\":0.95,\\"reason\\":\\"ok\\"}"}],
                     "stop_reason":"end_turn","stop_sequence":null,
                     "usage":{"input_tokens":300,"output_tokens":30,"cache_read_input_tokens":0}}
                    """)));
  }

  private CitationVerifier verifier() {
    ShromikaiProperties.Models models = VerifyFixtures.models(null);
    var claude =
        new AnthropicChatAdapter(
            request -> {
              seen.add(request.egressContext());
              EgressContext context = request.egressContext();
              String payload = request.system() + request.user().reveal();
              if (context != null
                  && context.protectedValues().stream().anyMatch(payload::contains)) {
                throw new EgressBlockedException("known-value");
              }
            },
            new ShromikaiProperties.Provider("claude-key", anthropic.baseUrl()),
            new ObjectMapper());
    ModelGateway gateway = VerifyFixtures.gateway(models, claude);
    return VerifyFixtures.verifier(new Corpus(), gateway, models);
  }

  private static VerificationContext context(OverlapChecker document) {
    EgressContext job =
        new EgressContext() {
          @Override
          public Set<String> protectedValues() {
            return Set.of(SECRET);
          }

          @Override
          public String analysisId() {
            return "analysis-1";
          }
        };
    return new VerificationContext(
        VerifyFixtures.TODAY, Set.of("XV"), "r", Set.of(KEY_NOTICE), 1, job, document);
  }

  @Test
  void shouldRaiseEgressBlockedWhenTheClaimHoldsAProtectedValue() {
    ClaimedCitation claim =
        VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, "The notice owed to " + SECRET + ".");

    assertThatThrownBy(() -> verifier().verify(claim, context(OverlapChecker.of("unrelated"))))
        .isInstanceOf(EgressBlockedException.class);
    anthropic.verify(0, postRequestedFor(urlEqualTo("/v1/messages")));
  }

  @Test
  void shouldPassTheJobsEgressContextToTheGateway() {
    Verdict verdict =
        verifier()
            .verify(
                VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, CLAIM_NOTICE),
                context(OverlapChecker.of("unrelated")));

    assertThat(seen).hasSize(1);
    assertThat(seen.getFirst()).isNotNull();
    assertThat(seen.getFirst().analysisId()).isEqualTo("analysis-1");
    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.VERIFIED);
  }

  @Test
  void shouldNotSendAClaimThatCopiesEightTokensOfTheDocument() {
    String copied =
        "the worker agrees to work any hours the employer decides without any extra pay";
    ClaimedCitation claim = VerifyFixtures.llmClaim(KEY_NOTICE, QUOTE_NOTICE, copied);

    Verdict verdict = verifier().verify(claim, context(OverlapChecker.of(copied)));

    anthropic.verify(0, postRequestedFor(urlEqualTo("/v1/messages")));
    assertThat(verdict.result().status()).isEqualTo(VerificationStatus.UNCERTAIN);
    assertThat(verdict.citation()).isNull();
  }
}
