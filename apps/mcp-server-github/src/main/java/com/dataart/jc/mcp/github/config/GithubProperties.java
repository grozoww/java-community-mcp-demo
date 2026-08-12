package com.dataart.jc.mcp.github.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the server needs to know about "which GitHub, and how far may the agent go".
 *
 * @param apiBaseUrl     GitHub REST base URL (github.com or GHES).
 * @param token          fine-grained PAT, scoped to the demo repository only.
 * @param allowedOwner   the only repository owner the agent may touch.
 * @param allowedRepo    the only repository name the agent may touch.
 * @param writeEnabled   master switch for every mutating tool.
 * @param protectedRefs  branches the agent may never commit to directly.
 * @param maxFileBytes   hard cap on how much file content may enter the model context.
 */
@ConfigurationProperties(prefix = "demo.github")
public record GithubProperties(
        String apiBaseUrl,
        String token,
        String allowedOwner,
        String allowedRepo,
        boolean writeEnabled,
        java.util.List<String> protectedRefs,
        int maxFileBytes) {

    public GithubProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://api.github.com";
        }
        if (protectedRefs == null || protectedRefs.isEmpty()) {
            protectedRefs = java.util.List.of("main", "master", "release", "production");
        }
        if (maxFileBytes <= 0) {
            maxFileBytes = 32_000;
        }
    }
}
