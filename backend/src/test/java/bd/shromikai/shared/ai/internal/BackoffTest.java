package bd.shromikai.shared.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.UpstreamUnavailableException;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Requirement("NFR-04")
class BackoffTest {

  private final List<Long> sleeps = new ArrayList<>();
  private final Backoff backoff = new Backoff(Duration.ofMillis(500), sleeps::add);

  @Test
  void shouldRetryTwiceWithDoublingDelay_whenFailureIsRetryable() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                backoff.call(
                    () -> {
                      attempts.incrementAndGet();
                      throw new UpstreamUnavailableException("x", 503, null);
                    }))
        .isInstanceOf(UpstreamUnavailableException.class);

    assertThat(attempts).hasValue(3);
    assertThat(sleeps).containsExactly(500L, 1000L);
  }

  @Test
  void shouldReturnResult_whenRetrySucceeds() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        backoff.call(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new UpstreamUnavailableException("x", 429, null);
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(sleeps).containsExactly(500L);
  }

  @Test
  void shouldNotRetry_whenStatusIsNotRetryable() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                backoff.call(
                    () -> {
                      attempts.incrementAndGet();
                      throw new UpstreamUnavailableException(
                          "x", UpstreamUnavailableException.NOT_CONFIGURED, null);
                    }))
        .isInstanceOf(UpstreamUnavailableException.class);

    assertThat(attempts).hasValue(1);
    assertThat(Backoff.retryable(400)).isFalse();
    assertThat(Backoff.retryable(408)).isTrue();
    assertThat(Backoff.retryable(0)).isTrue();
  }

  @Test
  void shouldBlockEveryCloudCall_untilThePrivacyGuardExists() {
    ChatRequest request =
        new ChatRequest(
            "xx-echo",
            1,
            ModelRole.FAST,
            "m",
            "s",
            SensitiveText.of("u"),
            new ObjectMapper().createObjectNode(),
            1,
            Duration.ofSeconds(1),
            null,
            null);

    assertThatThrownBy(() -> new FailClosedEgressGuard().check(request))
        .isInstanceOf(EgressBlockedException.class)
        .hasMessage(FailClosedEgressGuard.REASON);
  }
}
