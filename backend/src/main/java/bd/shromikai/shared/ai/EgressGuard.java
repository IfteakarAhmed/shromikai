package bd.shromikai.shared.ai;

/**
 * Checks every cloud-bound request before it leaves the box (R-03). Every cloud {@link ChatPort}
 * adapter calls it first; an adapter cannot be constructed without one.
 */
public interface EgressGuard {

  /**
   * @throws EgressBlockedException if the request must not be sent
   */
  void check(ChatRequest request);
}
