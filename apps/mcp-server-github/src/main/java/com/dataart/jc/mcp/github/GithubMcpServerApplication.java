package com.dataart.jc.mcp.github;

import com.dataart.jc.mcp.github.config.GithubProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MCP server that exposes a *deliberately small* slice of the GitHub REST API as MCP tools.
 *
 * <p>The point of the talk: GitHub's REST API has ~1000 endpoints. Nobody can hand that to a
 * model. An MCP server is where you decide which 13 of them an agent is allowed to think about,
 * and what the results look like once they hit the context window.
 */
@SpringBootApplication
@EnableConfigurationProperties(GithubProperties.class)
public class GithubMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GithubMcpServerApplication.class, args);
    }
}
