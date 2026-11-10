package bd.shromikai.shared.ai.internal;

import bd.shromikai.shared.ai.ChatRequest;
import bd.shromikai.shared.ai.EgressBlockedException;
import bd.shromikai.shared.ai.EgressGuard;

/**
 * Blocks every cloud call. It stands in until the privacy module provides the real egress guard
 * (P5): nothing may leave the box before the privacy shield exists (R-03).
 */
public final class FailClosedEgressGuard implements EgressGuard {

  public static final String REASON = "egress-guard-not-installed";

  @Override
  public void check(ChatRequest request) {
    throw new EgressBlockedException(REASON);
  }
}
