package com.dataart.jc.mcp.github.config;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tests that matter for an MCP server are the policy tests. Everything the model can reach is
 * an attack surface; everything the guard refuses is not.
 */
class RepoGuardTest {

    private final GithubProperties properties = new GithubProperties(
            "https://api.github.com", "token", "acme", "demo", true,
            List.of("main", "master"), 32_000);
    private final RepoGuard guard = new RepoGuard(properties);

    @Test
    void allowsTheAllowListedRepository() {
        assertThatCode(() -> guard.checkRepo("acme", "demo")).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkRepo("ACME", "Demo")).doesNotThrowAnyException();
    }

    @Test
    void rejectsEveryOtherRepository() {
        assertThatThrownBy(() -> guard.checkRepo("acme", "secrets"))
                .isInstanceOf(RepoGuard.PolicyViolation.class)
                .hasMessageContaining("allow-list");
    }

    @Test
    void rejectsWritesToProtectedBranches() {
        assertThatThrownBy(() -> guard.checkWrite("acme", "demo", "main"))
                .isInstanceOf(RepoGuard.PolicyViolation.class)
                .hasMessageContaining("protected");
        assertThatCode(() -> guard.checkWrite("acme", "demo", "agent/feature"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAllWritesWhenTheKillSwitchIsOff() {
        RepoGuard disabled = new RepoGuard(new GithubProperties(
                null, "token", "acme", "demo", false, null, 0));
        assertThatThrownBy(() -> disabled.checkWrite("acme", "demo", "agent/feature"))
                .isInstanceOf(RepoGuard.PolicyViolation.class);
    }
}
