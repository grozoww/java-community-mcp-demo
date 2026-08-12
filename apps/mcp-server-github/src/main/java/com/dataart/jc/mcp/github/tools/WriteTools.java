package com.dataart.jc.mcp.github.tools;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dataart.jc.mcp.github.client.GithubApiClient;
import com.dataart.jc.mcp.github.client.Json;
import com.dataart.jc.mcp.github.config.GithubProperties;
import com.dataart.jc.mcp.github.config.RepoGuard;
import com.dataart.jc.mcp.github.model.Models.BranchCreated;
import com.dataart.jc.mcp.github.model.Models.CommitResult;
import com.dataart.jc.mcp.github.model.Models.IssueCreated;
import com.dataart.jc.mcp.github.model.Models.PullRequestCreated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * The mutating half of the server - the part that makes the "self-modifying agent" demo possible,
 * and the part that needs a policy.
 *
 * <p>Three layers of defence, and they are deliberately independent:
 * <ol>
 *   <li>{@code readOnlyHint=false} / {@code destructiveHint=false} - a hint to the <em>client</em>,
 *       which is what drives the confirmation dialog in the chat UI.</li>
 *   <li>{@link RepoGuard} - a server-side allow-list. The model cannot argue with it.</li>
 *   <li>The token itself - fine-grained, scoped to one repository. Everything above is defence in
 *       depth; this is the actual boundary.</li>
 * </ol>
 */
@Service
public class WriteTools {

    private static final Logger log = LoggerFactory.getLogger(WriteTools.class);

    private final GithubApiClient api;
    private final RepoGuard guard;
    private final GithubProperties properties;

    public WriteTools(GithubApiClient api, RepoGuard guard, GithubProperties properties) {
        this.api = api;
        this.guard = guard;
        this.properties = properties;
    }

    @McpTool(
            name = "github_create_branch",
            description = """
                    Create a new branch from an existing ref. Always work on a branch: this server refuses to
                    commit to main, master, release or production.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true))
    public BranchCreated createBranch(
            @McpToolParam(description = "Repository owner") String owner,
            @McpToolParam(description = "Repository name") String repo,
            @McpToolParam(description = "New branch name, e.g. 'agent/add-hello-tool'") String branch,
            @McpToolParam(description = "Branch to fork from. Defaults to the repository default branch.",
                    required = false) String fromRef) {
        guard.checkWrite(owner, repo, branch);

        String base = fromRef == null || fromRef.isBlank() ? defaultBranch(owner, repo) : fromRef;
        Map<String, Object> ref = api.getObject(b -> b
                .path("/repos/{owner}/{repo}/git/ref/heads/" + base)
                .build(owner, repo));
        String sha = Json.str(Json.obj(ref, "object"), "sha");

        api.post(b -> b.path("/repos/{owner}/{repo}/git/refs").build(owner, repo),
                Map.of("ref", "refs/heads/" + branch, "sha", sha));

        log.info("Created branch {} from {} ({})", branch, base, sha);
        return new BranchCreated(branch, base, sha.substring(0, Math.min(7, sha.length())));
    }

    @McpTool(
            name = "github_commit_file",
            description = """
                    Create or replace a single text file on a branch and commit it. Pass the FULL new content
                    of the file - this is not a patch API. If the file already exists on that branch its blob
                    SHA is resolved automatically.

                    Use this together with github_create_branch and github_create_pull_request to propose a
                    change. Never commit to the default branch.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true))
    public CommitResult commitFile(
            @McpToolParam(description = "Repository owner") String owner,
            @McpToolParam(description = "Repository name") String repo,
            @McpToolParam(description = "Branch to commit to. Must not be a protected branch.") String branch,
            @McpToolParam(description = "File path relative to the repository root") String path,
            @McpToolParam(description = "Full new file content, UTF-8 text") String content,
            @McpToolParam(description = "Commit message, imperative mood, one line") String message) {
        guard.checkWrite(owner, repo, branch);

        if (content.length() > properties.maxFileBytes()) {
            throw new RepoGuard.PolicyViolation(
                    "Refusing to commit %d characters; the demo cap is %d. Split the change."
                            .formatted(content.length(), properties.maxFileBytes()));
        }

        String safePath = path.replaceFirst("^/", "");
        String existingSha = resolveBlobSha(owner, repo, safePath, branch);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        payload.put("branch", branch);
        if (existingSha != null) {
            payload.put("sha", existingSha);
        }

        Map<String, Object> result = api.put(
                b -> b.path("/repos/{owner}/{repo}/contents/" + safePath).build(owner, repo), payload);
        Map<String, Object> commit = Json.obj(result, "commit");

        log.info("Committed {} on {} ({})", safePath, branch, Json.str(commit, "sha"));
        return new CommitResult(
                safePath,
                branch,
                Json.clip(Json.str(commit, "sha", ""), 7),
                Json.str(Json.obj(result, "content"), "html_url"));
    }

    @McpTool(
            name = "github_create_pull_request",
            description = """
                    Open a pull request from a branch into the base branch. Write a body that explains WHY the
                    change is being proposed, not just what changed - a human will read it.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true))
    public PullRequestCreated createPullRequest(
            @McpToolParam(description = "Repository owner") String owner,
            @McpToolParam(description = "Repository name") String repo,
            @McpToolParam(description = "Source branch containing the change") String head,
            @McpToolParam(description = "Target branch. Defaults to the repository default branch.",
                    required = false) String base,
            @McpToolParam(description = "Pull request title") String title,
            @McpToolParam(description = "Pull request body in Markdown") String body) {
        // The head branch is what gets written to; the base is only a merge target.
        guard.checkWrite(owner, repo, head);
        String target = base == null || base.isBlank() ? defaultBranch(owner, repo) : base;

        Map<String, Object> pr = api.post(
                b -> b.path("/repos/{owner}/{repo}/pulls").build(owner, repo),
                Map.of("title", title, "head", head, "base", target, "body", body, "draft", true));

        log.info("Opened PR #{} {} -> {}", Json.i32(pr, "number"), head, target);
        return new PullRequestCreated(
                Json.i32(pr, "number"), head, target, Json.str(pr, "html_url", ""));
    }

    @McpTool(
            name = "github_create_issue",
            description = """
                    Create an issue. Use it when the right outcome is 'a human should look at this', rather
                    than proposing a code change yourself.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true))
    public IssueCreated createIssue(
            @McpToolParam(description = "Repository owner") String owner,
            @McpToolParam(description = "Repository name") String repo,
            @McpToolParam(description = "Issue title") String title,
            @McpToolParam(description = "Issue body in Markdown") String body,
            @McpToolParam(description = "Labels to apply, optional", required = false) List<String> labels) {
        guard.checkWrite(owner, repo, null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        if (labels != null && !labels.isEmpty()) {
            payload.put("labels", labels);
        }

        Map<String, Object> issue = api.post(
                b -> b.path("/repos/{owner}/{repo}/issues").build(owner, repo), payload);
        return new IssueCreated(Json.i32(issue, "number"), title, Json.str(issue, "html_url", ""));
    }

    private String defaultBranch(String owner, String repo) {
        Map<String, Object> body = api.getObject(b -> b.path("/repos/{owner}/{repo}").build(owner, repo));
        return Json.str(body, "default_branch", "main");
    }

    /** Returns the blob SHA of an existing file, or null when the file is new on this branch. */
    private String resolveBlobSha(String owner, String repo, String path, String branch) {
        try {
            Map<String, Object> body = api.getObject(b -> b
                    .path("/repos/{owner}/{repo}/contents/" + path)
                    .queryParam("ref", branch)
                    .build(owner, repo));
            return Json.str(body, "sha");
        } catch (GithubApiClient.GithubApiException e) {
            if (e.status() == 404) {
                return null;
            }
            throw e;
        }
    }
}
