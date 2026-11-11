package bd.shromikai.privacy;

import static bd.shromikai.testsupport.TestGateways.gateway;
import static bd.shromikai.testsupport.TestGateways.json;
import static bd.shromikai.testsupport.TestGateways.port;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.shared.ai.ChatPort;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.ChatResponse;
import bd.shromikai.shared.ai.LocalModelUnavailableException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.ai.TokenUsage;
import bd.shromikai.testsupport.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** FR-29, FR-31: local extraction in chunks, two attempts, fail closed. */
@Tag("unit")
@Requirement("FR-29")
@Requirement("FR-31")
class LocalEntityExtractorTest {

  private static final String ANSWER =
      "{\"status\":\"OK\",\"reason\":\"NONE\",\"persons\":[\"Rahim Uddin\"],"
          + "\"organisations\":[\"Example Ltd.\"],\"addresses\":[],\"employee_ids\":[]}";

  @Test
  void shouldMergeAndDeduplicateEntitiesAcrossChunks() {
    List<ChatRequest> seen = Collections.synchronizedList(new ArrayList<>());
    ChatPort ollama =
        port(
            "ollama",
            request -> {
              seen.add(request);
              return json(ANSWER);
            });
    var extractor = new LocalEntityExtractor(gateway(ollama));

    LocalEntities entities = extractor.extract("x".repeat(3200));

    assertThat(seen)
        .hasSizeGreaterThan(1)
        .allSatisfy(r -> assertThat(r.role()).isEqualTo(ModelRole.LOCAL_PII));
    assertThat(entities.persons()).containsExactly("Rahim Uddin");
    assertThat(entities.organisations()).containsExactly("Example Ltd.");
  }

  @Test
  void shouldCutChunksOf1500CharactersWith200Overlap() {
    List<String> chunks = LocalEntityExtractor.chunks("a".repeat(3000));

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0)).hasSize(1500);
    assertThat(chunks.get(1)).hasSize(1500);
    assertThat(chunks.get(0).substring(1300)).isEqualTo(chunks.get(1).substring(0, 200));
    assertThat(LocalEntityExtractor.chunks("short")).containsExactly("short");
    assertThat(LocalEntityExtractor.chunks("  ")).isEmpty();
  }

  @Test
  void shouldRetryOnceAfterAFailure() {
    AtomicInteger calls = new AtomicInteger();
    ChatPort flaky =
        new ChatPort() {
          public String provider() {
            return "ollama";
          }

          public ChatResponse call(ChatRequest request) {
            if (calls.incrementAndGet() == 1) {
              throw new LocalModelUnavailableException("ollama-unavailable");
            }
            return new ChatResponse(json(ANSWER), "stub", TokenUsage.NONE, false);
          }
        };

    LocalEntities entities = new LocalEntityExtractor(gateway(flaky)).extract("A short contract.");

    assertThat(calls).hasValue(2);
    assertThat(entities.persons()).containsExactly("Rahim Uddin");
  }

  @Test
  void shouldFailClosedWhenTheLocalModelFailsTwice() {
    AtomicInteger calls = new AtomicInteger();
    ChatPort down =
        new ChatPort() {
          public String provider() {
            return "ollama";
          }

          public ChatResponse call(ChatRequest request) {
            calls.incrementAndGet();
            throw new LocalModelUnavailableException("ollama-unavailable");
          }
        };

    assertThatThrownBy(() -> new LocalEntityExtractor(gateway(down)).extract("A short contract."))
        .isInstanceOf(LocalModelUnavailableException.class);
    assertThat(calls).hasValue(2);
  }

  @Test
  void shouldTreatAnAbstainAnswerAsNoEntities() {
    ChatPort ollama =
        port(
            "ollama",
            r ->
                json(
                    "{\"status\":\"ABSTAIN\",\"reason\":\"UNREADABLE\",\"persons\":[],"
                        + "\"organisations\":[],\"addresses\":[],\"employee_ids\":[]}"));

    assertThat(new LocalEntityExtractor(gateway(ollama)).extract("???"))
        .isEqualTo(LocalEntities.NONE);
  }
}
