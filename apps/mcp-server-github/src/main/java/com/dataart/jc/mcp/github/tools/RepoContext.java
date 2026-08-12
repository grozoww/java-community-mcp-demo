package com.dataart.jc.mcp.github.tools;

import com.dataart.jc.mcp.github.config.GithubProperties;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

/**
 * The two MCP primitives everybody forgets.
 *
 * <p><b>Resources</b> are application-controlled context: the host decides when to attach them.
 * Nothing is spent on them until someone asks. <b>Prompts</b> are user-controlled workflows -
 * the slash commands of MCP. Both exist precisely so that not everything has to be a tool.
 */
@Service
public class RepoContext {

    private final GithubProperties properties;

    public RepoContext(GithubProperties properties) {
        this.properties = properties;
    }

    @McpResource(
            uri = "github://policy",
            name = "github-access-policy",
            title = "What this agent may do on GitHub",
            description = "The allow-list and write policy enforced by this MCP server.",
            mimeType = "text/markdown")
    public String policy() {
        return """
                # GitHub access policy

                - Repository allow-list: `%s/%s` (any other repository is rejected server-side)
                - Write tools enabled: **%s**
                - Protected refs (never committed to directly): %s
                - Maximum file size read or written: %d bytes

                Every mutating tool is marked `readOnlyHint=false` so the host can require explicit
                human approval before it runs.
                """.formatted(
                properties.allowedOwner(),
                properties.allowedRepo(),
                properties.writeEnabled(),
                String.join(", ", properties.protectedRefs()),
                properties.maxFileBytes());
    }

    @McpPrompt(
            name = "propose-change",
            title = "Propose a change as a pull request",
            description = "Standard workflow: read the code, branch, commit, open a draft PR.")
    public String proposeChange(
            @McpArg(name = "goal", description = "What should change and why", required = true) String goal) {
        return """
                You are proposing a change to %s/%s.

                Goal: %s

                Follow this order and do not skip steps:
                1. `github_get_repository` to find the default branch.
                2. `github_search_code` / `github_list_files` / `github_read_file` to locate the code. Read as
                   few files as you can get away with.
                3. `github_create_branch` with a descriptive name under `agent/`.
                4. `github_commit_file` with the FULL new content of each file you change.
                5. `github_create_pull_request` with a body that explains the reasoning.

                If any step is refused by policy, stop and report it to the user. Do not try a workaround.
                """.formatted(properties.allowedOwner(), properties.allowedRepo(), goal);
    }
}
