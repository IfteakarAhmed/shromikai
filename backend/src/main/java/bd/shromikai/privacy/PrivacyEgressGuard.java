package bd.shromikai.privacy;

import bd.shromikai.privacy.internal.AhoCorasick;
import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.EgressContext;
import bd.shromikai.shared.ai.EgressGuard;
import bd.shromikai.trace.ActorKind;
import bd.shromikai.trace.AuditAction;
import bd.shromikai.trace.AuditEvent;
import bd.shromikai.trace.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The egress guard (FR-30, R-03, architecture §10.5). Every cloud {@code ChatPort} adapter passes
 * the fully rendered request here. Stage 1 re-runs the deterministic detectors on the payload;
 * stage 2 searches it for every original value of the job's placeholder map (Aho–Corasick, case
 * insensitive). A hit throws {@link EgressBlockedException} ({@code privacy-guard-blocked}), writes
 * a {@code PRIVACY_BLOCK} audit event and counts {@code shromikai_privacy_blocks_total}. The audit
 * event carries a reason code and the analysis ID only, never the value.
 */
@Component
public final class PrivacyEgressGuard implements EgressGuard {

  public static final String REASON_DETECTOR = "pii_detected";
  public static final String REASON_KNOWN_VALUE = "known_value_found";

  private static final Logger LOG = LoggerFactory.getLogger(PrivacyEgressGuard.class);

  private final PiiDetector detector;
  private final AuditService audit;
  private final MeterRegistry meters;

  public PrivacyEgressGuard(PiiDetector detector, AuditService audit, MeterRegistry meters) {
    this.detector = detector;
    this.audit = audit;
    this.meters = meters;
  }

  @Override
  public void check(ChatRequest request) {
    String payload = request.system() + "\n" + request.user().reveal();
    String reason = null;
    if (detector.containsPii(payload)) {
      reason = REASON_DETECTOR;
    } else {
      EgressContext context = request.egressContext();
      if (context != null && containsKnownValue(payload, context.protectedValues())) {
        reason = REASON_KNOWN_VALUE;
      }
    }
    if (reason == null) {
      return;
    }
    block(request, reason);
  }

  private static boolean containsKnownValue(String payload, Set<String> values) {
    if (values.isEmpty()) {
      return false;
    }
    Set<String> lowered = new HashSet<>();
    for (String value : values) {
      String v = value.strip();
      if (!v.isEmpty()) {
        lowered.add(v.toLowerCase(Locale.ROOT));
      }
    }
    return new AhoCorasick(lowered).matches(payload.toLowerCase(Locale.ROOT));
  }

  private void block(ChatRequest request, String reason) {
    meters.counter("shromikai_privacy_blocks_total").increment();
    EgressContext context = request.egressContext();
    String analysisId = context == null ? null : context.analysisId();
    try {
      audit.record(
          AuditEvent.of(null, ActorKind.SYSTEM, AuditAction.PRIVACY_BLOCK, Map.of("reason", reason))
              .withTarget("analysis", analysisId));
    } catch (RuntimeException e) {
      // The block must hold even if the trail cannot be written; log the fact, never the payload.
      LOG.error("privacy block could not be audited, reason={}", reason);
    }
    throw new EgressBlockedException(reason.replace('_', '-'));
  }
}
