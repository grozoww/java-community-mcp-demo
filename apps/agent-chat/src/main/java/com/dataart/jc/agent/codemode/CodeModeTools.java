package com.dataart.jc.agent.codemode;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.dataart.jc.agent.config.AgentProperties;
import com.dataart.jc.agent.mcp.McpToolCatalog;
import com.dataart.jc.agent.telemetry.TokenMeter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Code Mode in three tools.
 *
 * <p>However many MCP tools are connected - 13 or 1300 - the model sees these three. It discovers
 * what exists on demand ({@code list_tools}), pulls the full signature only for what it needs
 * ({@code describe_tools}), and then does the actual work in one script ({@code run_script}) where
 * loops, filtering and intermediate results never touch the context window.
 */
@Component
public class CodeModeTools {

    private final McpToolCatalog catalog;
    private final JsSandbox sandbox;
    private final AgentProperties properties;
    private final Pattern writeTools;

    public CodeModeTools(McpToolCatalog catalog, JsSandbox sandbox, AgentProperties properties) {
        this.catalog = catalog;
        this.sandbox = sandbox;
        this.properties = properties;
        this.writeTools = Pattern.compile(properties.writeToolPattern());
    }

    @Tool(description = """
            List the operations available in the `mcp` API, one line each, optionally filtered by a
            substring. Start here. Returns names and short descriptions only - not full signatures.""")
    public String list_tools(
            @ToolParam(description = "Substring filter, e.g. 'issue' or 'metric'. Empty for everything.",
                    required = false) String filter) {
        return ApiSurface.index(catalog.raw(), filter);
    }

    @Tool(description = """
            Return the full TypeScript signatures and documentation for specific operations.
            Call it only for the handful you are about to use.""")
    public String describe_tools(
            @ToolParam(description = "Comma-separated operation names, e.g. 'github_list_issues,github_read_file'")
            String names) {
        List<String> wanted = Arrays.stream(names.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return ApiSurface.declarations(
                catalog.raw().stream()
                        .filter(tool -> wanted.contains(tool.getToolDefinition().name()))
                        .toList());
    }

    @Tool(description = """
            Run a JavaScript program that calls the `mcp` API and prints its conclusions.

            Environment:
              - `mcp.<operation>({ ...args })` returns the parsed result of an MCP tool call.
              - `print(...)` / `console.log(...)` is the ONLY channel back to you.
              - No network, no file system, no imports. Plain ECMAScript plus `mcp`.

            Write the whole task as one program: loop, filter, aggregate, and print only the few lines
            you actually need. Anything you do not print costs nothing. Anything you print, you pay for
            on every later turn.

            Example:
              const issues = mcp.github_list_issues({ owner: 'o', repo: 'r', state: 'open', limit: 30 });
              const stale = issues.filter(i => i.updatedAt < '2026-01-01');
              print('stale:', stale.length, stale.slice(0, 3).map(i => i.number));""")
    public String run_script(
            @ToolParam(description = "The JavaScript program to execute") String code) {
        McpBridge bridge = new McpBridge(catalog.raw(), writeTools, false);
        JsSandbox.Result result = sandbox.run(code, bridge, properties.scriptTimeoutMillis());

        StringBuilder response = new StringBuilder();
        response.append(result.ok() ? "OK" : "FAILED")
                .append(" in ").append(result.millis()).append(" ms, ")
                .append(result.toolCalls()).append(" mcp call(s)\n");
        if (result.error() != null) {
            response.append("error: ").append(result.error()).append('\n');
        }
        response.append("--- output ---\n").append(result.output());

        String text = response.toString();
        return text + "\n(this result is ~%d tokens)".formatted(TokenMeter.estimate(text));
    }
}
