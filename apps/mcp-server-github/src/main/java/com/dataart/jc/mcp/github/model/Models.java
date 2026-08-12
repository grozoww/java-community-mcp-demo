package com.dataart.jc.mcp.github.model;

import java.util.List;

/**
 * Agent-facing result shapes.
 *
 * <p>These are NOT the GitHub API's response shapes. A single GitHub issue payload is ~90 JSON
 * fields; the model needs six of them. Every field you forward is a field the model pays for on
 * every subsequent turn of the conversation, because tool results stay in the transcript.
 *
 * <p>Rule of thumb from this demo: shape tool results like a good log line, not like a REST
 * resource.
 */
public final class Models {

    private Models() {
    }

    public record RepoSummary(
            String fullName,
            String description,
            String defaultBranch,
            String language,
            int openIssues,
            int stars,
            String pushedAt,
            String url) {
    }

    public record FileEntry(String path, String type, long sizeBytes) {
    }

    public record FileContent(
            String path,
            String ref,
            String sha,
            int bytes,
            boolean truncated,
            String content) {
    }

    public record IssueSummary(
            int number,
            String title,
            String state,
            String author,
            List<String> labels,
            String updatedAt,
            String url) {
    }

    public record IssueComment(String author, String body) {
    }

    public record IssueDetail(
            int number,
            String title,
            String state,
            String author,
            List<String> labels,
            String body,
            List<IssueComment> comments,
            String url) {
    }

    public record PullRequestSummary(
            int number,
            String title,
            String state,
            String author,
            String head,
            String base,
            boolean draft,
            String url) {
    }

    public record WorkflowRunSummary(
            long id,
            String workflow,
            String status,
            String conclusion,
            String branch,
            String headSha,
            String createdAt,
            String url) {
    }

    public record CodeHit(String path, String url) {
    }

    public record BranchCreated(String branch, String fromRef, String sha) {
    }

    public record CommitResult(String path, String branch, String commitSha, String url) {
    }

    public record PullRequestCreated(int number, String head, String base, String url) {
    }

    public record IssueCreated(int number, String title, String url) {
    }

    public record Identity(String login, String name, String type, List<String> scopesHint) {
    }
}
