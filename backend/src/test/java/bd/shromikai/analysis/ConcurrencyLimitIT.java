package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import bd.shromikai.auth.Role;
import bd.shromikai.shared.Hashing;
import bd.shromikai.testsupport.AnthropicStub;
import bd.shromikai.testsupport.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * NFR-20: at most 5 analyses run at once per organisation and one per guest device; the sixth (or
 * second) is {@code 429 concurrency-limit}. A slot is free again when the analysis ends.
 */
@Requirement("NFR-20")
class ConcurrencyLimitIT extends ApiAnalysisIT {

  private final CountDownLatch release = new CountDownLatch(1);

  /** Holds every analysis at CLASSIFY until the test lets go. */
  private void holdAtClassify() {
    fake.respondWith(
        "clause-classify",
        request -> {
          try {
            release.await(30, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return AnthropicStub.answer(request.system() + "\n" + request.user().reveal());
        });
  }

  @AfterEach
  void letGo() {
    release.countDown();
  }

  @Test
  void theSixthConcurrentAnalysisOfAnOrganisationIsRefusedAndASlotFreesWhenOneEnds()
      throws Exception {
    UUID org =
        jdbc.sql(
                "insert into organisation (name, kind, plan) values ('Busy', 'HR', 'TEAM') returning id")
            .query(UUID.class)
            .single();
    try {
      holdAtClassify();
      String[] tokens = new String[6];
      for (int i = 0; i < 6; i++) {
        tokens[i] = jwt.issueUser(UUID.randomUUID(), Role.HR, org, false).value();
      }
      String firstId = null;
      for (int i = 0; i < 5; i++) {
        ResponseEntity<JsonNode> accepted = postMultipart(tokens[i], text());
        assertThat(accepted.getStatusCode())
            .as("analysis " + (i + 1))
            .isEqualTo(HttpStatus.ACCEPTED);
        if (firstId == null) {
          firstId = accepted.getBody().path("analysisId").asText();
        }
      }

      ResponseEntity<JsonNode> sixth = postMultipart(tokens[5], text());

      assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      assertThat(sixth.getBody().path("code").asText()).isEqualTo("concurrency-limit");

      release.countDown();
      awaitResult(tokens[0], firstId);
      for (int i = 0;
          i < 100 && postMultipart(tokens[5], text()).getStatusCode() != HttpStatus.ACCEPTED;
          i++) {
        Thread.sleep(100);
      }
      assertThat(postMultipart(tokens[5], text()).getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      release.countDown();
      Thread.sleep(500);
      jdbc.sql("delete from analysis_run").update();
      jdbc.sql("delete from organisation where id = :id").param("id", org).update();
    }
  }

  @Test
  void aGuestDeviceMayRunOneAnalysisAtATime() {
    holdAtClassify();
    String guest = jwt.issueGuest(Hashing.sha256Hex("device-" + UUID.randomUUID())).value();

    ResponseEntity<JsonNode> first = postMultipart(guest, text());
    ResponseEntity<JsonNode> second = postMultipart(guest, text());

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(second.getBody().path("code").asText()).isEqualTo("concurrency-limit");
  }
}
