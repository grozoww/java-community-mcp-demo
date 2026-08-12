package com.dataart.jc.agent.approval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Human-in-the-loop, implemented as boringly as possible.
 *
 * <p>An approval is granted for one conversation + tool + exact argument string, and it is consumed
 * the first time it is used. The model cannot widen it by re-asking, and it cannot reuse yesterday's
 * "yes" for today's arguments.
 *
 * <p>In production this belongs in the same place as your audit log, not in a map.
 */
@Component
public class ApprovalRegistry {

    private final Map<String, PendingApproval> pending = new ConcurrentHashMap<>();
    private final Map<String, PendingApproval> granted = new ConcurrentHashMap<>();

    public PendingApproval request(String conversation, String toolName, String arguments) {
        String key = key(conversation, toolName, arguments);
        PendingApproval approval = new PendingApproval(
                UUID.randomUUID().toString().substring(0, 8), conversation, toolName, arguments, Instant.now());
        pending.put(key, approval);
        return approval;
    }

    /** @return true when this exact call was approved; the approval is consumed. */
    public boolean consume(String conversation, String toolName, String arguments) {
        return granted.remove(key(conversation, toolName, arguments)) != null;
    }

    public boolean approve(String approvalId) {
        return pending.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(approvalId))
                .findFirst()
                .map(entry -> {
                    pending.remove(entry.getKey());
                    granted.put(entry.getKey(), entry.getValue());
                    return true;
                })
                .orElse(false);
    }

    public boolean reject(String approvalId) {
        return pending.values().removeIf(approval -> approval.id().equals(approvalId));
    }

    public List<PendingApproval> pendingFor(String conversation) {
        return pending.values().stream()
                .filter(approval -> approval.conversation().equals(conversation))
                .toList();
    }

    private static String key(String conversation, String toolName, String arguments) {
        return conversation + "|" + toolName + "|" + arguments;
    }
}
