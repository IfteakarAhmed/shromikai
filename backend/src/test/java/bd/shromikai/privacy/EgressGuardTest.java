package bd.shromikai.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bd.shromikai.shared.SensitiveText;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.ModelRole;
import bd.shromikai.shared.problem.ProblemType;
import bd.shromikai.testsupport.Requirement;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** FR-30, R-03: the egress guard blocks and audits; the audit never holds the value. */
@Tag("unit")
@Requirement("FR-30")
@Requirement("NFR-06")
class EgressGuardTest {

  private final AuditService audit = mock(AuditService.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private PrivacyEgressGuard guard;

  @BeforeEach
  void setUp() {
    guard = new PrivacyEgressGuard(new PiiDetector(), audit, meters);
  }

  private static ChatRequest request(String user, PlaceholderMap map) {
    return new ChatRequest(
        "clause-classify",
        1,
        ModelRole.FAST,
        "claude-haiku-5-5",
        "Classify clauses.",
        SensitiveText.of(user),
        new ObjectMapper().createObjectNode(),
        100,
        Duration.ofSeconds(5),
        null,
        null,
        map);
  }

  @Test
  void shouldBlockAndAuditAPayloadThatStillContainsANid() {
    var map = new PlaceholderMap("analysis-1");

    assertThatThrownBy(() -> guard.check(request("The worker, NID No: 1234567890, joins.", map)))
        .isInstanceOfSatisfying(
            EgressBlockedException.class,
            e -> assertThat(e.type()).isEqualTo(ProblemType.PRIVACY_GUARD_BLOCKED));

    ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
    verify(audit).record(event.capture());
    assertThat(event.getValue().action()).isEqualTo(AuditAction.PRIVACY_BLOCK);
    assertThat(event.getValue().actorKind()).isEqualTo(ActorKind.SYSTEM);
    assertThat(event.getValue().targetId()).isEqualTo("analysis-1");
    assertThat(event.getValue().detail().toString()).doesNotContain("1234567890");
    assertThat(meters.counter("shromikai_privacy_blocks_total").count()).isEqualTo(1.0);
  }

  @Test
  void shouldBlockAnOriginalValueOfTheJobEvenWhenNoDetectorFindsIt() {
    var map = new PlaceholderMap("analysis-2");
    map.placeholderFor(PiiType.PERSON, "Abdul Karim Sheikh");

    assertThatThrownBy(
            () -> guard.check(request("Employee abdul karim sheikh shall report to HR.", map)))
        .isInstanceOf(EgressBlockedException.class);

    ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
    verify(audit).record(event.capture());
    assertThat(event.getValue().detail()).containsEntry("reason", "known_value_found");
    assertThat(event.getValue().detail().toString()).doesNotContainIgnoringCase("karim");
  }

  @Test
  void shouldLetAFullyRedactedPayloadThrough() {
    var map = new PlaceholderMap("analysis-3");
    map.placeholderFor(PiiType.PERSON, "Abdul Karim Sheikh");

    assertThatCode(
            () ->
                guard.check(request("[PERSON_1] earns BDT 25,000 per month from 01/01/2026.", map)))
        .doesNotThrowAnyException();

    verify(audit, never()).record(any());
    assertThat(meters.find("shromikai_privacy_blocks_total").counter()).isNull();
  }

  @Test
  void shouldStillBlockWhenTheAuditTrailCannotBeWritten() {
    doThrow(new IllegalStateException("db down")).when(audit).record(any());

    assertThatThrownBy(
            () -> guard.check(request("mail rahim@example.org please", new PlaceholderMap())))
        .isInstanceOf(EgressBlockedException.class);
  }

  @Test
  void shouldRunTheDetectorsEvenWithoutAnEgressContext() {
    var req = request("call 01712345678", null);

    assertThatThrownBy(() -> guard.check(req)).isInstanceOf(EgressBlockedException.class);
  }

  @Test
  void shouldCheckTheSystemTurnToo() {
    var req =
        new ChatRequest(
            "x",
            1,
            ModelRole.FAST,
            "m",
            "Contact admin@example.org",
            SensitiveText.of("ok"),
            new ObjectMapper().createObjectNode(),
            10,
            Duration.ofSeconds(1),
            null,
            null);

    assertThatThrownBy(() -> guard.check(req)).isInstanceOf(EgressBlockedException.class);
  }
}
