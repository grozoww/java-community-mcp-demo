package com.dataart.jc.agent.approval;

import java.time.Instant;

/**
 * One request from the agent for permission to change something.
 *
 * @param id            opaque id shown to the user and echoed back on approval.
 * @param conversation  the conversation the request belongs to.
 * @param toolName      the MCP tool the model wants to call.
 * @param arguments     the exact arguments it wants to call it with, as JSON.
 * @param requestedAt   when the model asked.
 */
public record PendingApproval(
        String id,
        String conversation,
        String toolName,
        String arguments,
        Instant requestedAt) {
}
