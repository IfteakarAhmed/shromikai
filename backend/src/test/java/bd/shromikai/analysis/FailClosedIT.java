package bd.shromikai.analysis;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.privacy.PiiDetector;
import bd.shromikai.privacy.PrivacyEgressGuard;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.shared.ai.anthropic.AnthropicChatAdapter;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.AnalysisHarness;
import bd.shromikai.testsupport.AnthropicStub;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.SyntheticContracts;
import bd.shromikai.testsupport.TestGateways;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** FR-31, R-03: with the local model down the analysis fails and no cloud call is made. */
@Tag("it")
@Requirement("FR-31")
@Requirement("NFR-06")
class FailClosedIT {

  @RegisterExtension
  static WireMockExtension cloud =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .http2PlainDisabled(true)
                  .extensions(new AnthropicStub()))
          .build();

  @Test
  void shouldFailWithLocalModelUnavailableAndMakeZeroCloudRequests() {
    cloud.stubFor(post(anyUrl()).willReturn(aResponse().withTransformers(AnthropicStub.NAME)));
    AtomicInteger ollamaCalls = new AtomicInteger();
    ChatPort ollamaDown =
        new ChatPort() {
          public String provider() {
            return "ollama";
          }

          public ChatResponse call(ChatRequest request) {
            ollamaCalls.incrementAndGet();
            throw new LocalModelUnavailableException("ollama-unavailable");
          }
        };
    AnalysisHarness[] h = new AnalysisHarness[1];
    var guard =
        new PrivacyEgressGuard(
            new PiiDetector(),
            org.mockito.Mockito.mock(bd.shromikai.trace.AuditService.class),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    ChatPort anthropic =
        new AnthropicChatAdapter(
            guard,
            new ShromikaiProperties.Provider("test-key", cloud.baseUrl()),
            TestGateways.MAPPER);
    h[0] = new AnalysisHarness(anthropic, ollamaDown, image -> null);

    AnalysisJob job = h[0].service.analyseText(SyntheticContracts.text("syn-en-01"));

    assertThat(job.status()).isEqualTo(AnalysisJob.Status.FAILED);
    assertThat(job.failure().type()).isEqualTo(ProblemType.LOCAL_MODEL_UNAVAILABLE);
    assertThat(job.stage()).isEqualTo(Stage.REDACT);
    assertThat(ollamaCalls.get()).isGreaterThanOrEqualTo(2); // two attempts, then fail closed
    assertThat(cloud.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    assertThat(job.trace())
        .anySatisfy(
            e -> {
              assertThat(e.stage()).isEqualTo(Stage.REDACT);
              assertThat(e.outcome()).isEqualTo(TraceEvent.FAILED);
            });
    assertThat(job.clauses()).isEmpty();
  }
}
