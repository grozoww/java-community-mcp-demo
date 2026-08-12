package com.dataart.jc.mcp.github.tools;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.dataart.jc.mcp.github.client.GithubApiClient;
import com.dataart.jc.mcp.github.client.Json;
import com.dataart.jc.mcp.github.config.GithubProperties;
import com.dataart.jc.mcp.github.config.RepoGuard;
import com.dataart.jc.mcp.github.model.Models.CodeHit;
import com.dataart.jc.mcp.github.model.Models.FileContent;
import com.dataart.jc.mcp.github.model.Models.FileEntry;
import com.dataart.jc.mcp.github.model.Models.Identity;
import com.dataart.jc.mcp.github.model.Models.RepoSummary;
import com.dataart.jc.mcp.github.model.Models.WorkflowRunSummary;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * Read-only view of one repository.
 *
 * <p>Descriptions here are not documentation - they are the prompt. This is the single biggest
 * behavioural lever an MCP server author has, and the one most often written as an afterthought.
 */
@Service
public class RepositoryTools {

    static final String OWNER = "Repository owner. Optional, defaults to the allowed repository.";
    static final String REPO = "Repository name. Optional, defaults to the allowed repository.";

    private final GithubApiClient api;
    private final RepoGuard guard;
    private final GithubProperties properties;

    public RepositoryTools(GithubApiClient api, RepoGuard guard, GithubProperties properties) {
        this.api = api;
        this.guard = guard;
        this.properties = properties;
    }

    @McpTool(
            name = "github_whoami",
            description = """
                    Return the GitHub identity this server is authenticated as, and the single repository
                    it is allowed to touch. Call this first if you are unsure what you have access to.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public Identity whoAmI() {
        Map<String, Object> user = api.getObject(b -> b.path("/user").build());
        return new Identity(
                Json.str(user, "login"),
                Json.str(user, "name", ""),
                Json.str(user, "type", "User"),
                List.of("repo:" + properties.allowedOwner() + "/" + properties.allowedRepo(),
                        properties.writeEnabled() ? "write:enabled" : "write:disabled"));
    }

    @McpTool(
            name = "github_get_repository",
            description = """
                    Get high-level metadata for a repository: default branch, primary language, open issue
                    count and last push time. Use it to discover the default branch before reading files.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public RepoSummary getRepository(
            @McpToolParam(description = OWNER, required = false) String owner,
            @McpToolParam(description = REPO, required = false) String repo) {
        RepoGuard.Target target = guard.resolve(owner, repo);
        Map<String, Object> body = api.getObject(
                b -> b.path("/repos/{owner}/{repo}").build(target.owner(), target.repo()));
        return new RepoSummary(
                Json.str(body, "full_name"),
                Json.clip(Json.str(body, "description", ""), 300),
                Json.str(body, "default_branch", "main"),
                Json.str(body, "language", "unknown"),
                Json.i32(body, "open_issues_count"),
                Json.i32(body, "stargazers_count"),
                Json.str(body, "pushed_at", ""),
                Json.str(body, "html_url", ""));
    }

    @McpTool(
            name = "github_list_files",
            description = """
                    List the entries of one directory in the repository tree. Returns names, types and sizes
                    only - never file contents. Use it to navigate before calling github_read_file, so you
                    do not pull whole directories into context.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public List<FileEntry> listFiles(
            @McpToolParam(description = OWNER, required = false) String owner,
            @McpToolParam(description = REPO, required = false) String repo,
            @McpToolParam(description = "Directory path, empty string for the repository root", required = false)
            String path,
            @McpToolParam(description = "Branch, tag or commit SHA. Defaults to the default branch.", required = false)
            String ref) {
        RepoGuard.Target target = guard.resolve(owner, repo);
        String safePath = path == null ? "" : path.replaceFirst("^/", "");
        // NOTE: the file path is concatenated into the template, not passed as a URI variable.
        // URI variables are encoded, which would turn "src/main/java" into "src%2Fmain%2Fjava".
        List<Map<String, Object>> entries = api.getArray(b -> b
                .path("/repos/{owner}/{repo}/contents/" + safePath)
                .queryParamIfPresent("ref", java.util.Optional.ofNullable(blankToNull(ref)))
                .build(target.owner(), target.repo()));
        return entries.stream()
                .map(entry -> new FileEntry(
                        Json.str(entry, "path"),
                        Json.str(entry, "type"),
                        Json.i64(entry, "size")))
                .toList();
    }

    @McpTool(
            name = "github_read_file",
            description = """
                    Read one text file from the repository. Content is decoded and hard-capped; if the file is
                    larger than the cap the result is marked truncated. Prefer reading a single file over
                    listing many - each file you read stays in the conversation for the rest of the session.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public FileContent readFile(
            @McpToolParam(description = OWNER, required = false) String owner,
            @McpToolParam(description = REPO, required = false) String repo,
            @McpToolParam(description = "File path relative to the repository root") String path,
            @McpToolParam(description = "Branch, tag or commit SHA. Defaults to the default branch.", required = false)
            String ref) {
        RepoGuard.Target target = guard.resolve(owner, repo);
        String safePath = path.replaceFirst("^/", "");
        Map<String, Object> body = api.getObject(b -> b
                .path("/repos/{owner}/{repo}/contents/" + safePath)
                .queryParamIfPresent("ref", java.util.Optional.ofNullable(blankToNull(ref)))
                .build(target.owner(), target.repo()));

        String encoded = Json.str(body, "content", "");
        byte[] decoded = Base64.getMimeDecoder().decode(encoded.isBlank() ? "" : encoded);
        boolean truncated = decoded.length > properties.maxFileBytes();
        String text = new String(
                truncated ? java.util.Arrays.copyOf(decoded, properties.maxFileBytes()) : decoded,
                java.nio.charset.StandardCharsets.UTF_8);

        return new FileContent(
                Json.str(body, "path"),
                ref == null || ref.isBlank() ? "(default branch)" : ref,
                Json.str(body, "sha"),
                decoded.length,
                truncated,
                text);
    }

    @McpTool(
            name = "github_search_code",
            description = """
                    Search code inside the allowed repository and return matching file paths only.
                    Use this to locate a class or a configuration key before reading files. The search is
                    always scoped to the allow-listed repository, whatever you pass in.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public List<CodeHit> searchCode(
            @McpToolParam(description = "Free-text or GitHub code-search query, e.g. 'McpTool extension:java'")
            String query,
            @McpToolParam(description = "Maximum number of hits, 1-20. Default 10.", required = false)
            Integer limit) {
        int max = limit == null ? 10 : Math.clamp(limit, 1, 20);
        String scoped = "%s repo:%s/%s".formatted(query, properties.allowedOwner(), properties.allowedRepo());
        Map<String, Object> body = api.getObject(b -> b
                .path("/search/code")
                .queryParam("q", scoped)
                .queryParam("per_page", max)
                .build());
        return Json.arr(body, "items").stream()
                .map(item -> new CodeHit(Json.str(item, "path"), Json.str(item, "html_url", "")))
                .toList();
    }

    @McpTool(
            name = "github_list_workflow_runs",
            description = """
                    List the most recent GitHub Actions runs with their status and conclusion. Use it to answer
                    'is the build green' or to find the run that failed before proposing a fix.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public List<WorkflowRunSummary> listWorkflowRuns(
            @McpToolParam(description = OWNER, required = false) String owner,
            @McpToolParam(description = REPO, required = false) String repo,
            @McpToolParam(description = "Restrict to a branch, optional", required = false) String branch,
            @McpToolParam(description = "Maximum number of runs, 1-20. Default 5.", required = false) Integer limit) {
        RepoGuard.Target target = guard.resolve(owner, repo);
        int max = limit == null ? 5 : Math.clamp(limit, 1, 20);
        Map<String, Object> body = api.getObject(b -> b
                .path("/repos/{owner}/{repo}/actions/runs")
                .queryParamIfPresent("branch", java.util.Optional.ofNullable(blankToNull(branch)))
                .queryParam("per_page", max)
                .build(target.owner(), target.repo()));
        return Json.arr(body, "workflow_runs").stream()
                .map(run -> new WorkflowRunSummary(
                        Json.i64(run, "id"),
                        Json.str(run, "name", ""),
                        Json.str(run, "status", ""),
                        Json.str(run, "conclusion", "in_progress"),
                        Json.str(run, "head_branch", ""),
                        Json.str(run, "head_sha", "").substring(0, Math.min(7, Json.str(run, "head_sha", "").length())),
                        Json.str(run, "created_at", ""),
                        Json.str(run, "html_url", "")))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
