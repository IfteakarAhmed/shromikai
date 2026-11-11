package bd.shromikai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.shromikai.privacy.PiiType;
import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.json.JsonSchemas;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.testsupport.TestGateways;
import bd.shromikai.verify.SignedResultCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * FR-54, FR-55, T-07: names are restored only in the response to the owner; the job keeps
 * placeholders; the result is signed as returned; it lives 30 minutes from completion.
 */
@Tag("unit")
@Requirement("FR-54")
@Requirement("FR-55")
class AnalysisResultsTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();
  private static final AnalysisSubject OWNER =
      new AnalysisSubject("USER", Hashing.sha256Hex("owner"), null, "API");
  private static final AnalysisSubject OTHER =
      new AnalysisSubject("USER", Hashing.sha256Hex("other"), null, "API");

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-16T10:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MutableClock clock = new MutableClock();
  private PipelineHarness harness;
  private AnalysisResults results;
  private SignedResultCodec codec;
  private AnalysisJob job;

  @BeforeEach
  void run() {
    harness =
        new PipelineHarness(
            request ->
                request.promptId().equals("risk-analysis")
                    ? TestGateways.json(
                        """
                        {"status":"OK","reason":"NONE","findings":[{"title":"Dismissal on short notice",
                         "kind":"CONFLICT","severity":"HIGH","non_obvious":false,
                         "clause_type":"NOTICE_AND_RESIGNATION","clause_ids":["c1"],
                         "explanation":"[PERSON_1] gets only seven days notice of dismissal.",
                         "citations":[{"section_key":"XV-LAB:9@original",
                           "quote":"shall give a worker written notice of dismissal at least fourteen days",
                           "claim":"An employer must give a worker written notice before dismissal."}],
                         "confidence":0.8}]}
                        """)
                    : null,
            new AnalysisFixtures.StubSource(q -> List.of(AnalysisFixtures.KEY_NOTICE)),
            List.of(AnalysisFixtures.noticeMinRule()),
            PipelineHarness.properties(),
            clock);
    job = harness.classifiedJob();
    job.placeholders().placeholderFor(PiiType.PERSON, "Karim Uddin");
    harness.pipeline.run(job, AnalysisRequest.of(Side.WORKER), OWNER);
    codec =
        new SignedResultCodec(
            "unit-test-envelope-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
            "k1",
            Duration.ofHours(24),
            clock);
    results =
        new AnalysisResults(
            harness.jobs,
            new bd.shromikai.memo.MemoComposer(MAPPER),
            codec,
            MAPPER,
            clock,
            new MockEnvironment());
  }

  @Test
  void namesAreRestoredInTheResponseButTheJobKeepsPlaceholders() {
    ObjectNode result = results.resultFor(job.id(), OWNER);

    assertThat(result.path("findings").get(0).path("explanation").asText())
        .contains("Karim Uddin")
        .doesNotContain("[PERSON_1]");
    assertThat(result.path("memo").toString()).contains("Karim Uddin");
    // what the server keeps: placeholders only
    assertThat(job.outcome().findings().get(0).explanation().reveal()).contains("[PERSON_1]");
    assertThat(job.trace().toString()).doesNotContain("Karim Uddin");
  }

  @Test
  void theResultCarriesAValidMemoRedlinesObligationsAndASignedEnvelope() {
    ObjectNode result = results.resultFor(job.id(), OWNER);

    assertThat(new JsonSchemas().validate("schemas/api/memo.v1.schema.json", result.path("memo")))
        .isEmpty();
    assertThat(result.path("redlines").get(0).path("diff")).isNotEmpty();
    assertThat(result.path("obligations")).hasSize(2);
    assertThat(result.path("summary").path("verifiedFindings").asInt()).isEqualTo(1);
    assertThat(result.path("disclaimer").asText()).isNotBlank();
    // a client sends the result back minus its envelope; it must open
    ObjectNode envelope = (ObjectNode) result.path("envelope");
    ObjectNode payload = result.deepCopy();
    payload.remove("envelope");
    JsonNode opened =
        codec.open(
            new SignedResultCodec.Envelope(
                payload,
                envelope.path("keyId").asText(),
                envelope.path("algorithm").asText(),
                Instant.parse(envelope.path("expiresAt").asText()),
                envelope.path("signature").asText()));
    assertThat(opened.path("analysisId").asText()).isEqualTo(job.id().toString());
  }

  @Test
  void aChangedByteInTheReturnedResultIsRejectedByTheCodec() {
    ObjectNode result = results.resultFor(job.id(), OWNER);
    ObjectNode envelope = (ObjectNode) result.path("envelope");
    ObjectNode payload = result.deepCopy();
    payload.remove("envelope");
    ((ObjectNode) payload.path("findings").get(0)).put("title", "Dismissal on short notice!");

    assertThatThrownBy(
            () ->
                codec.open(
                    new SignedResultCodec.Envelope(
                        payload,
                        envelope.path("keyId").asText(),
                        envelope.path("algorithm").asText(),
                        Instant.parse(envelope.path("expiresAt").asText()),
                        envelope.path("signature").asText())))
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.SIGNATURE_INVALID));
  }

  @Test
  void anotherSubjectGetsNotFound() {
    assertThatThrownBy(() -> results.resultFor(job.id(), OTHER))
        .isInstanceOfSatisfying(
            ProblemException.class, p -> assertThat(p.type()).isEqualTo(ProblemType.NOT_FOUND));
    assertThatThrownBy(() -> results.resultFor(UUID.randomUUID(), OWNER))
        .isInstanceOfSatisfying(
            ProblemException.class, p -> assertThat(p.type()).isEqualTo(ProblemType.NOT_FOUND));
  }

  @Test
  void theResultIsThereAt29MinutesAndGoneAt31ForTheOwnerWith410() {
    clock.advance(Duration.ofMinutes(29));
    assertThat(results.resultFor(job.id(), OWNER).path("analysisId").asText())
        .isEqualTo(job.id().toString());

    clock.advance(Duration.ofMinutes(2));
    assertThatThrownBy(() -> results.resultFor(job.id(), OWNER))
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.ANALYSIS_EXPIRED));
    assertThatThrownBy(() -> results.resultFor(job.id(), OTHER))
        .isInstanceOfSatisfying(
            ProblemException.class, p -> assertThat(p.type()).isEqualTo(ProblemType.NOT_FOUND));
  }

  @Test
  void afterDeleteTheOwnerGets410AndThePlaceholderMapIsGone() {
    var map = job.placeholders();

    harness.jobs.delete(job.id());

    assertThat(map.size()).isZero();
    assertThatThrownBy(() -> results.resultFor(job.id(), OWNER))
        .isInstanceOfSatisfying(
            ProblemException.class,
            p -> assertThat(p.type()).isEqualTo(ProblemType.ANALYSIS_EXPIRED));
  }
}
