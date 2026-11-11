package bd.shromikai.memo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** FR-55: the Gotenberg contract, retries on 5xx, and renderer-unavailable when it is down. */
@Tag("it")
@Requirement("FR-55")
class GotenbergClientTest {

  private static final String PATH = "/forms/chromium/convert/html";

  @RegisterExtension
  static WireMockExtension gotenberg =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private GotenbergClient client() {
    return new GotenbergClient(
        new MemoProperties(
            "http://localhost:" + gotenberg.getPort(),
            Duration.ofSeconds(5),
            Duration.ofMillis(1)));
  }

  @Test
  void postsTheHtmlAsIndexHtmlAndReturnsThePdfBytes() {
    gotenberg.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(aResponse().withStatus(200).withBody("%PDF-1.7 fake".getBytes())));

    byte[] pdf = client().pdf("<html><body>নোটিস</body></html>");

    assertThat(new String(pdf)).startsWith("%PDF");
    gotenberg.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            .withRequestBody(containing("filename=\"index.html\""))
            .withRequestBody(containing("নোটিস")));
  }

  @Test
  void retriesATransientFailureOnce() {
    gotenberg.stubFor(
        post(urlPathEqualTo(PATH))
            .inScenario("flaky")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("up"));
    gotenberg.stubFor(
        post(urlPathEqualTo(PATH))
            .inScenario("flaky")
            .whenScenarioStateIs("up")
            .willReturn(aResponse().withStatus(200).withBody("%PDF-1.7 ok".getBytes())));

    assertThat(new String(client().pdf("<p>x</p>"))).startsWith("%PDF");
  }

  @Test
  void aPersistentServerErrorIsRendererUnavailableAfterThreeAttempts() {
    gotenberg.resetAll();
    gotenberg.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client().pdf("<p>x</p>"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.RENDERER_UNAVAILABLE));
    gotenberg.verify(3, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void aClientErrorIsNotRetried() {
    gotenberg.resetAll();
    gotenberg.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> client().pdf("<p>x</p>")).isInstanceOf(ProblemException.class);
    gotenberg.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void anUnreachableRendererIsRendererUnavailable() {
    GotenbergClient down =
        new GotenbergClient(
            new MemoProperties("http://localhost:1", Duration.ofSeconds(1), Duration.ofMillis(1)));

    assertThatThrownBy(() -> down.pdf("<p>x</p>"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.RENDERER_UNAVAILABLE));
  }
}
