package bd.shromikai.testsupport.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.CostCalculator;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.shared.ai.fake.FakeChatPort;
import bd.shromikai.shared.config.PricingProperties;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-0015 decision 4: the live-spend ledger refuses a call that could pass the US$3 cap, survives
 * between runs, and the recorder writes fixtures that the fake port replays.
 */
@Tag("unit")
@Requirement("FR-48")
class LiveSpendLedgerTest {

  @TempDir Path dir;

  private static final CostCalculator COST =
      new CostCalculator(
          new PricingProperties(
              Map.of(
                  "claude-haiku-5-5",
                  new PricingProperties.Price(0.1, 0.5, 0.125, 0.2, 0.01, null))));

  private static ChatRequest request(String prompt) {
    return new ChatRequest(
        prompt,
        1,
        ModelRole.FAST,
        "claude-haiku-5-5",
        "system",
        SensitiveText.of("user text"),
        TestGateways.json("{\"type\":\"object\"}"),
        100,
        Duration.ofSeconds(5),
        null,
        null);
  }

  private static ChatPort answering(JsonNode out, long in, long outTokens) {
    return new ChatPort() {
      @Override
      public String provider() {
        return "anthropic";
      }

      @Override
      public ChatResponse call(ChatRequest request) {
        return new ChatResponse(
            out, "claude-haiku-5-5", new TokenUsage(in, outTokens, 0, 0), false);
      }
    };
  }

  @Test
  void shouldCapSpendAtThreeDollars() {
    assertThat(LiveSpendLedger.CAP_MICROS).isEqualTo(3_000_000);
  }

  @Test
  void shouldAccumulateAcrossInstancesAndRefuseACallThatCouldPassTheCap() {
    Path file = dir.resolve("ledger.json");
    LiveSpendLedger first = new LiveSpendLedger(file, 1_000);
    first.record("one", "m", 10, 10, 700);

    LiveSpendLedger second = new LiveSpendLedger(file, 1_000);

    assertThat(second.spentMicros()).isEqualTo(700);
    assertThat(second.remainingMicros()).isEqualTo(300);
    second.reserve("fits", 300);
    assertThatThrownBy(() -> second.reserve("too much", 301))
        .isInstanceOf(LiveSpendLedger.SpendCapException.class)
        .hasMessageContaining("Ask the owner");
  }

  @Test
  void shouldRefuseBeforeTheCallIsMade_andRecordUsageAfterwards() {
    LiveSpendLedger ledger = new LiveSpendLedger(dir.resolve("l.json"), 5_000);
    CappedChatPort capped =
        new CappedChatPort(answering(TestGateways.json("{}"), 1000, 50), ledger, COST, "t", 10);

    capped.call(request("a"));

    // 1000 in at 0.1/M + 50 out at 0.5/M = 0.000125 = 125 micro-dollars
    assertThat(ledger.spentMicros()).isEqualTo(125);
    assertThat(ledger.callsFor("t:a")).isEqualTo(1);

    LiveSpendLedger tight = new LiveSpendLedger(dir.resolve("tight.json"), 1);
    CappedChatPort refused =
        new CappedChatPort(
            new ChatPort() {
              @Override
              public String provider() {
                return "anthropic";
              }

              @Override
              public ChatResponse call(ChatRequest request) {
                throw new AssertionError("must not be called");
              }
            },
            tight,
            COST,
            "t",
            10);
    assertThatThrownBy(() -> refused.call(request("a")))
        .isInstanceOf(LiveSpendLedger.SpendCapException.class);
  }

  @Test
  void shouldNeverMakeMoreCallsThanTheRunAllows() {
    LiveSpendLedger ledger = new LiveSpendLedger(dir.resolve("m.json"), 1_000_000);
    CappedChatPort capped =
        new CappedChatPort(answering(TestGateways.json("{}"), 1, 1), ledger, COST, "t", 2);

    capped.call(request("a"));
    capped.call(request("a"));

    assertThatThrownBy(() -> capped.call(request("a")))
        .isInstanceOf(LiveSpendLedger.SpendCapException.class)
        .hasMessageContaining("More than 2 live calls");
    assertThat(capped.calls()).isEqualTo(3);
    assertThat(ledger.callsFor("t")).isEqualTo(2);
  }

  @Test
  void shouldRecordFixturesThatTheFakePortReplaysByRequestHash() throws Exception {
    Path root = dir.resolve("fixtures/llm");
    JsonNode output =
        TestGateways.json("{\"status\":\"OK\",\"reason\":\"NONE\",\"queries\":[\"a\"]}");
    RecordingChatPort recorder = new RecordingChatPort(answering(output, 10, 5), root, "demo-case");
    ChatRequest first = request("query-rewrite");
    ChatRequest second =
        new ChatRequest(
            "query-rewrite",
            1,
            ModelRole.FAST,
            "claude-haiku-5-5",
            "system",
            SensitiveText.of("another user text"),
            TestGateways.json("{\"type\":\"object\"}"),
            100,
            Duration.ofSeconds(5),
            null,
            null);

    recorder.call(first);
    recorder.call(second);

    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {dir.resolve("fixtures").toUri().toURL()})) {
      FakeChatPort fake =
          new FakeChatPort("llm", TestGateways.MAPPER, new JsonSchemas(), "demo-case", loader);
      assertThat(fake.call(first).output().path("status").asText()).isEqualTo("OK");
      assertThat(fake.call(second).recorded()).isTrue();
      ChatRequest changed =
          new ChatRequest(
              "query-rewrite",
              1,
              ModelRole.FAST,
              "claude-haiku-5-5",
              "system",
              SensitiveText.of("a third text that was never recorded"),
              TestGateways.json("{\"type\":\"object\"}"),
              100,
              Duration.ofSeconds(5),
              null,
              null);
      assertThatThrownBy(() -> fake.call(changed))
          .hasMessageContaining("No fixture for query-rewrite");
    }
  }
}
