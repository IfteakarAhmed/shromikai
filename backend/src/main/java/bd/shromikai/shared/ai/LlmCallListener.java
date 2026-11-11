package bd.shromikai.shared.ai;

/**
 * Receives one {@link LlmCall} per finished gateway call, for metrics and the cost ledger
 * (architecture §6 adapter rule 4). Implementations must not throw and must not keep content.
 */
public interface LlmCallListener {

  void onCall(LlmCall call);
}
