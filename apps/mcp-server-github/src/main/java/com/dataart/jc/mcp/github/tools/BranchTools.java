package com.dataart.jc.mcp.github.tools;

import java.util.List;
import java.util.Map;

import com.dataart.jc.mcp.github.client.GithubApiClient;
import com.dataart.jc.mcp.github.client.Json;
import com.dataart.jc.mcp.github.config.GithubProperties;
import com.dataart.jc.mcp.github.config.RepoGuard;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * Tools for interacting with GitHub branches.
 */
@Service
public class BranchTools {

    static final String OWNER = "Repository owner. Optional, defaults to the allowed repository.";
    static final String REPO = "Repository name. Optional, defaults to the allowed repository.";

    private final GithubApiClient api;
    private final RepoGuard guard;
    private final GithubProperties properties;

    public BranchTools(GithubApiClient api, RepoGuard guard, GithubProperties properties) {
        this.api = api;
        this.guard = guard;
        this.properties = properties;
    }

    @McpTool(
            name = "github_list_branches",
            description = """
                    List all branches for a repository.
            """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public List<String> listBranches(
            @McpToolParam(description = OWNER, required = false) String owner,
            @McpToolParam(description = REPO, required = false) String repo) {
        RepoGuard.Target target = guard.resolve(owner, repo);
        List<Map<String, Object>> branches = api.getArray(b -> b
                .path("/repos/{owner}/{repo}/branches")
                .build(target.owner(), target.repo()));
        return branches.stream()
                .map(branch -> Json.str(branch, "name"))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}