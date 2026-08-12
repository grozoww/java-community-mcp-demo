package com.dataart.jc.agent.codemode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

/**
 * The only host object visible inside the sandbox.
 *
 * <p>{@code HostAccess.EXPLICIT} means the JavaScript side can reach exactly one method - the one
 * annotated below. No reflection, no classloader, no {@code java.*}. This is the security boundary
 * that Code Mode buys with an entire extra runtime.
 */
public class McpBridge {

    private static final Logger log = LoggerFactory.getLogger(McpBridge.class);

    private final List<ToolCallback> tools;
    private final Pattern writeTools;
    private final boolean allowWrites;
    private final AtomicInteger callCount = new AtomicInteger();

    public McpBridge(List<ToolCallback> tools, Pattern writeTools, boolean allowWrites) {
        this.tools = tools;
        this.writeTools = writeTools;
        this.allowWrites = allowWrites;
    }

    @HostAccess.Export
    public String call(String name, String argumentsJson) {
        if (writeTools.matcher(name).matches() && !allowWrites) {
            // An honest limitation, and a good slide: a script that runs 40 calls in one shot has no
            // natural place to stop and ask a human. Confirmation flows want one call at a time.
            throw new IllegalStateException(
                    "Tool '%s' changes state and cannot be called from a script. Leave code mode and call it directly."
                            .formatted(name));
        }
        ToolCallback tool = tools.stream()
                .filter(candidate -> candidate.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown tool '%s'. Call list_tools() to see what exists.".formatted(name)));

        callCount.incrementAndGet();
        log.debug("code-mode call #{}: {}({})", callCount.get(), name, argumentsJson);
        return tool.call(argumentsJson);
    }

    public int callCount() {
        return callCount.get();
    }
}
