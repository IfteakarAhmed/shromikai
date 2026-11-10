package bd.shromikai.shared.ai.fake;

import bd.shromikai.shared.ai.ChatRequest;

/** One call received by {@link FakeChatPort}, kept for assertions. */
public record RecordedCall(
    String promptId, int promptVersion, String caseId, ChatRequest request) {}
