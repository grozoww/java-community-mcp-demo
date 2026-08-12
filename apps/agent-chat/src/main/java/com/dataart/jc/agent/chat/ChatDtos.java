package com.dataart.jc.agent.chat;

import java.util.List;

import com.dataart.jc.agent.approval.PendingApproval;
import com.dataart.jc.agent.telemetry.TurnTrace;

public final class ChatDtos {

    private ChatDtos() {
    }

    /** How the agent is allowed to reach its tools this turn. */
    public enum Mode {
        /** Classic MCP: every tool definition is in the prompt, the model emits tool calls. */
        TOOLS,
        /** Code execution: three meta-tools, the model writes a script instead. */
        CODE,
        /** No tools at all - useful to show what the model knows on its own. */
        PLAIN
    }

    public record ChatRequest(String conversationId, String message, Mode mode) {
    }

    public record TokenReport(
            int toolDefinitionsInContext,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens) {
    }

    public record ChatResponsePayload(
            String conversationId,
            Mode mode,
            String reply,
            List<TurnTrace.Entry> toolCalls,
            List<PendingApproval> approvals,
            TokenReport tokens,
            long millis) {
    }

    public record ToolInfo(String name, String description, int totalTokens) {
    }
}
