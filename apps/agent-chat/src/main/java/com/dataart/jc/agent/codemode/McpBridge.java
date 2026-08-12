package com.dataart.jc.agent.codemode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only host object visible inside the sandbox.
 *
 * <p>{@code HostAccess.EXPLICIT} means the JavaScript side can reach exactly one method - the one
 * annotated below. No reflection, no classloader, no {@code java.*}. This is the security boundary
 * that Code Mode buys with an entire extra runtime.
 */
public class McpBridge {

    private static final Logger log = LoggerFactory.getLogger(McpBridge.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
        return unwrap(tool.call(argumentsJson));
    }

    /**
     * Turns the MCP content envelope back into the payload the tool actually returned.
     *
     * <p>{@code ToolCallback.call} hands back the protocol's content blocks -
     * {@code [{"text":"..."}]} - and not the tool's own JSON. The sandbox prelude runs
     * {@code JSON.parse} over this, so without unwrapping every script receives an array with a
     * text field: {@code repo.fullName} is undefined, the script prints nothing, and it looks for
     * all the world like the tool returned an empty result. The value handed back here is always
     * valid JSON, because the prelude is going to parse it.
     */
    private static String unwrap(String raw) {
        if (raw == null || raw.isBlank()) {
            return "null";
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isArray()) {
                return raw;
            }
            StringBuilder payload = new StringBuilder();
            for (JsonNode block : node) {
                JsonNode text = block.get("text");
                if (text == null || !text.isString()) {
                    return raw;   // an image or resource block: hand the envelope over untouched
                }
                payload.append(text.asString());
            }
            String unwrapped = payload.toString();
            return isJson(unwrapped) ? unwrapped : JSON.writeValueAsString(unwrapped);
        } catch (RuntimeException e) {
            return raw;
        }
    }

    private static boolean isJson(String value) {
        try {
            JSON.readTree(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public int callCount() {
        return callCount.get();
    }
}
