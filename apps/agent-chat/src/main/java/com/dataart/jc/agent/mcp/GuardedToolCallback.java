package com.dataart.jc.agent.mcp;

import java.util.regex.Pattern;

import com.dataart.jc.agent.approval.ApprovalRegistry;
import com.dataart.jc.agent.approval.PendingApproval;
import com.dataart.jc.agent.telemetry.TurnTrace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Wraps an MCP tool so that mutating calls stop and ask a human first.
 *
 * <p>The interesting design decision: instead of throwing, the guard returns a normal tool result
 * that says "approval required". The model reads it, explains itself to the user, and waits. No
 * special-casing in the agent loop, no exception handling, no side channel - just a tool result the
 * model can reason about.
 */
public class GuardedToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(GuardedToolCallback.class);

    private final ToolCallback delegate;
    private final ApprovalRegistry approvals;
    private final TurnTrace trace;
    private final Pattern writeTools;

    public GuardedToolCallback(ToolCallback delegate,
                               ApprovalRegistry approvals,
                               TurnTrace trace,
                               Pattern writeTools) {
        this.delegate = delegate;
        this.approvals = approvals;
        this.trace = trace;
        this.writeTools = writeTools;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = getToolDefinition().name();
        String conversation = ConversationScope.current();

        if (!writeTools.matcher(name).matches()) {
            return invoke(conversation, name, toolInput, toolContext, "ok");
        }
        if (approvals.consume(conversation, name, toolInput)) {
            log.info("Approved call to {} in conversation {}", name, conversation);
            return invoke(conversation, name, toolInput, toolContext, "approved");
        }

        PendingApproval approval = approvals.request(conversation, name, toolInput);
        log.info("Approval {} requested for {} in conversation {}", approval.id(), name, conversation);
        String result = """
                {"status":"approval_required","approvalId":"%s","tool":"%s",\
                "message":"This call changes state and needs explicit human approval. \
                Summarise for the user exactly what you intend to do and why, then stop. \
                Do not call any other tool. When the user approves, call this same tool again with \
                identical arguments."}"""
                .formatted(approval.id(), name);
        trace.record(conversation, new TurnTrace.Entry(name, toolInput, result, 0, "approval_required"));
        return result;
    }

    private String invoke(String conversation, String name, String toolInput,
                          ToolContext toolContext, String status) {
        long start = System.nanoTime();
        String result;
        String outcome = status;
        try {
            result = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
        } catch (RuntimeException e) {
            // A failed tool call is information, not a crash. Hand the message back to the model so it
            // can correct itself - that single decision removes most "agent got stuck" reports.
            result = "{\"error\":\"%s\"}".formatted(String.valueOf(e.getMessage()).replace('"', '\''));
            outcome = "error";
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        trace.record(conversation, new TurnTrace.Entry(name, toolInput, clip(result), millis, outcome));
        return result;
    }

    private static String clip(String value) {
        return value == null ? "" : value.length() > 2000 ? value.substring(0, 2000) + "..." : value;
    }
}
