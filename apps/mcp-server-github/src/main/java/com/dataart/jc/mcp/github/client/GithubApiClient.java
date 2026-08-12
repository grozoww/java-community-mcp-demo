package com.dataart.jc.mcp.github.client;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Thin, untyped wrapper over the GitHub REST API.
 *
 * <p>Untyped on purpose: this class is the "API" half of the talk. Notice how little it knows about
 * intent - it moves JSON. All of the agent-facing meaning (naming, descriptions, result shaping,
 * safety) lives one layer up, in the MCP tools.
 */
@Component
public class GithubApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ARRAY =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public GithubApiClient(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    public Map<String, Object> getObject(Function<UriBuilder, URI> uriFunction) {
        Map<String, Object> body = restClient.get()
                .uri(uriFunction)
                .retrieve()
                .onStatus(HttpStatusCode::isError, GithubApiClient::translate)
                .body(OBJECT);
        return body == null ? Map.of() : body;
    }

    public List<Map<String, Object>> getArray(Function<UriBuilder, URI> uriFunction) {
        List<Map<String, Object>> body = restClient.get()
                .uri(uriFunction)
                .retrieve()
                .onStatus(HttpStatusCode::isError, GithubApiClient::translate)
                .body(ARRAY);
        return body == null ? List.of() : body;
    }

    public Map<String, Object> post(Function<UriBuilder, URI> uriFunction, Object payload) {
        Map<String, Object> body = restClient.post()
                .uri(uriFunction)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, GithubApiClient::translate)
                .body(OBJECT);
        return body == null ? Map.of() : body;
    }

    public Map<String, Object> put(Function<UriBuilder, URI> uriFunction, Object payload) {
        Map<String, Object> body = restClient.put()
                .uri(uriFunction)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, GithubApiClient::translate)
                .body(OBJECT);
        return body == null ? Map.of() : body;
    }

    /**
     * Turns an HTTP failure into one short, actionable sentence.
     *
     * <p>A GitHub 422 with the full validation envelope is a few hundred tokens of noise that the
     * model will dutifully try to reason about. One sentence is cheaper and works better. Result
     * shaping is not cosmetics - it is context budget management.
     */
    private static void translate(HttpRequest request, ClientHttpResponse response) throws IOException {
        String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String message = raw.length() > 400 ? raw.substring(0, 400) + "..." : raw;
        throw new GithubApiException(response.getStatusCode().value(), message);
    }

    public static class GithubApiException extends RuntimeException {
        private final int status;

        public GithubApiException(int status, String message) {
            super("GitHub API returned HTTP %d: %s".formatted(status, message));
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
