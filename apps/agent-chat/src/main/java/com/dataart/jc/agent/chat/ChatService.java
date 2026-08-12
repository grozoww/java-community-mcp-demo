package com.dataart.jc.agent.chat;

import java.util.List;

import com.dataart.jc.agent.approval.ApprovalRegistry;
import com.dataart.jc.agent.chat.ChatDtos.ChatResponsePayload;
import com.dataart.jc.agent.chat.ChatDtos.Mode;
import com.dataart.jc.agent.chat.ChatDtos.TokenReport;
import com.dataart.jc.agent.codemode.CodeModeTools;
import com.dataart.jc.agent.config.AgentProperties;
import com.dataart.jc.agent.mcp.ConversationScope;
import com.dataart.jc.agent.mcp.McpToolCatalog;
import com.dataart.jc.agent.telemetry.TokenMeter;
import com.dataart.jc.agent.telemetry.TurnTrace;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * One turn of the agent loop.
 *
 * <p>Note how little there is here. Spring AI's ChatClient already runs the loop: model produces a
 * tool call, the tool-calling advisor executes it, the result goes back, repeat until the model
 * answers in prose. What this class owns is the interesting part - which tools are visible, and who
 * is allowed to approve what.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final McpToolCatalog catalog;
    private final CodeModeTools codeModeTools;
    private final TurnTrace trace;
    private final ApprovalRegistry approvals;

    public ChatService(ChatClient agentChatClient,
                       McpToolCatalog catalog,
                       CodeModeTools codeModeTools,
                       TurnTrace trace,
                       ApprovalRegistry approvals,
                       AgentProperties properties) {
        this.chatClient = agentChatClient;
        this.catalog = catalog;
        this.codeModeTools = codeModeTools;
        this.trace = trace;
        this.approvals = approvals;
    }

    public ChatResponsePayload chat(String conversationId, String message, Mode mode) {
        trace.start(conversationId);
        long start = System.nanoTime();

        ChatResponse response;
        try {
            response = ConversationScope.with(conversationId, () -> {
                ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                        .user(message)
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId));

                spec = switch (mode) {
                    case TOOLS -> spec.tools(catalog.guarded());
                    case CODE -> spec.tools(codeModeTools);
                    case PLAIN -> spec;
                };
                return spec.call().chatResponse();
            });
        } catch (Exception e) {
            throw new IllegalStateException("Agent turn failed: " + e.getMessage(), e);
        }

        long millis = (System.nanoTime() - start) / 1_000_000;
        String reply = response == null || response.getResult() == null
                ? "(no content)"
                : response.getResult().getOutput().getText();

        return new ChatResponsePayload(
                conversationId,
                mode,
                reply,
                trace.drain(conversationId),
                approvals.pendingFor(conversationId),
                tokenReport(mode, response),
                millis);
    }

    /**
     * The number that sells the second half of the talk: what the tool definitions alone cost,
     * before the user has typed a single word.
     */
    private TokenReport tokenReport(Mode mode, ChatResponse response) {
        int definitionsCost = switch (mode) {
            case TOOLS -> catalog.contextCostInTokens();
            case CODE -> codeModeContextCost();
            case PLAIN -> 0;
        };
        var usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new TokenReport(
                definitionsCost,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens());
    }

    private int codeModeContextCost() {
        // Three fixed tool definitions, whatever the number of MCP servers behind them.
        List<String> descriptions = List.of("list_tools", "describe_tools", "run_script");
        return descriptions.stream().mapToInt(TokenMeter::estimate).sum() + 420;
    }
}
