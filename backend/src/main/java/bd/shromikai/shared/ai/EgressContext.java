package bd.shromikai.shared.ai;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What the egress guard needs to know about the job a cloud call belongs to (R-03, FR-30): the
 * original values behind its placeholders. Implemented by the privacy module's placeholder map; the
 * values never leave JVM memory and are never logged.
 */
public interface EgressContext {

  /** Every original PII value of the job. The guard blocks a payload that contains any of them. */
  Set<String> protectedValues();

  /** The analysis the call belongs to, for the audit trail; {@code null} outside an analysis. */
  @Nullable String analysisId();
}
