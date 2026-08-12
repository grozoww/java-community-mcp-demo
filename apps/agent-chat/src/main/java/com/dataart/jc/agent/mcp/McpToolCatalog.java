package com.dataart.jc.agent.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.dataart.jc.agent.approval.ApprovalRegistry;
import com.dataart.jc.agent.config.AgentProperties;
import com.dataart.jc.agent.telemetry.TokenMeter;
import com.dataart.jc.agent.telemetry.TurnTrace;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * Everything the agent knows how to do, in one place.
 *
 * <p>Spring AI's MCP client starter connects to every configured server at startup, calls
 * {@code tools/list} on each, and exposes the union as tool callbacks. This class does three things
 * on top: wraps them with the approval guard, measures what they cost in context, and hands the
 * catalogue to the Code Mode side of the demo.
 */
@Component
public class McpToolCatalog {

    private final List<ToolCallback> guarded;
    private final List<ToolCallback> raw;

    public McpToolCatalog(List<ToolCallbackProvider> providers,
                          ApprovalRegistry approvals,
                          TurnTrace trace,
                          AgentProperties properties) {
        Pattern writeTools = Pattern.compile(properties.writeToolPattern());
        this.raw = providers.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toList();
        this.guarded = raw.stream()
                .map(callback -> (ToolCallback) new GuardedToolCallback(callback, approvals, trace, writeTools))
                .toList();
    }

    /** Tool callbacks with the human-approval guard applied - what the ChatClient actually gets. */
    public ToolCallback[] guarded() {
        return guarded.toArray(ToolCallback[]::new);
    }

    /** Unwrapped callbacks - used by the Code Mode bridge, which does its own guarding. */
    public List<ToolCallback> raw() {
        return raw;
    }

    public ToolCallback byName(String name) {
        return raw.stream()
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such tool: " + name));
    }

    public List<Map<String, Object>> describe() {
        return raw.stream()
                .map(callback -> {
                    var definition = callback.getToolDefinition();
                    return Map.<String, Object>of(
                            "name", definition.name(),
                            "description", definition.description(),
                            "schemaTokens", TokenMeter.estimate(definition.inputSchema()),
                            "totalTokens", TokenMeter.estimate(
                                    definition.name() + definition.description() + definition.inputSchema()));
                })
                .sorted((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))))
                .toList();
    }

    /**
     * What it costs, per turn, to have all of these tools available.
     *
     * <p>This number is the whole reason the second half of the talk exists.
     */
    public int contextCostInTokens() {
        return raw.stream()
                .map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name() + definition.description() + definition.inputSchema())
                .mapToInt(TokenMeter::estimate)
                .sum();
    }

    public String namesJoined() {
        return raw.stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.joining(", "));
    }
}
