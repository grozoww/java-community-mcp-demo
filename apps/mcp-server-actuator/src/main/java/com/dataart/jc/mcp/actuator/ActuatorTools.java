package com.dataart.jc.mcp.actuator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Eight tools over Spring Boot Actuator.
 *
 * <p>Read them as an answer to "what does a well-designed MCP tool look like": one verb, a result
 * small enough to read out loud, and a description that tells the model when NOT to call it.
 */
@Service
public class ActuatorTools {

    private static final Logger log = LoggerFactory.getLogger(ActuatorTools.class);
    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;
    private final ActuatorProperties properties;

    public ActuatorTools(RestClient actuatorRestClient, ActuatorProperties properties) {
        this.client = actuatorRestClient;
        this.properties = properties;
    }

    public record Health(String status, Map<String, String> components) { }

    public record MetricValue(String name, String description, String baseUnit, Map<String, Double> measurements) { }

    public record LoggerLevel(String name, String configuredLevel, String effectiveLevel) { }

    public record ThreadSummary(int total, Map<String, Long> byState, List<String> topStacks) { }

    @McpTool(
            name = "app_health",
            description = "Overall health of the running application plus the status of each health contributor.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public Health health() {
        Map<String, Object> body = get("/health");
        Map<String, Object> components = asMap(body.get("components"));
        return new Health(
                String.valueOf(body.getOrDefault("status", "UNKNOWN")),
                components.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(asMap(e.getValue()).getOrDefault("status", "UNKNOWN")),
                        (a, b) -> a,
                        java.util.TreeMap::new)));
    }

    @McpTool(
            name = "app_list_metrics",
            description = """
                    List the names of available metrics, optionally filtered by substring. Call this before
                    app_metric so you do not guess metric names. Returns names only, no values.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public List<String> listMetrics(
            @McpToolParam(description = "Substring filter, e.g. 'jvm.memory' or 'http'", required = false)
            String filter) {
        Map<String, Object> body = get("/metrics");
        List<?> names = (List<?>) body.getOrDefault("names", List.of());
        return names.stream()
                .map(String::valueOf)
                .filter(name -> filter == null || filter.isBlank() || name.contains(filter))
                .sorted()
                .limit(80)
                .toList();
    }

    @McpTool(
            name = "app_metric",
            description = "Read one metric by its exact name, e.g. 'jvm.memory.used' or 'http.server.requests'.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public MetricValue metric(
            @McpToolParam(description = "Exact metric name from app_list_metrics") String name) {
        Map<String, Object> body = get("/metrics/" + name);
        Map<String, Double> measurements = new java.util.LinkedHashMap<>();
        for (Object measurement : (List<?>) body.getOrDefault("measurements", List.of())) {
            Map<String, Object> m = asMap(measurement);
            measurements.put(String.valueOf(m.get("statistic")),
                    m.get("value") instanceof Number n ? n.doubleValue() : 0d);
        }
        return new MetricValue(
                String.valueOf(body.getOrDefault("name", name)),
                String.valueOf(body.getOrDefault("description", "")),
                String.valueOf(body.getOrDefault("baseUnit", "")),
                measurements);
    }

    @McpTool(
            name = "app_environment",
            description = """
                    Look up configuration properties by prefix. Secrets are masked by Actuator itself.
                    Use it to answer 'which model is this agent configured with' or 'where does it think the
                    MCP server is'.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public Map<String, String> environment(
            @McpToolParam(description = "Property name prefix, e.g. 'spring.ai.ollama'") String prefix) {
        Map<String, Object> body = get("/env");
        Map<String, String> result = new java.util.TreeMap<>();
        for (Object source : (List<?>) body.getOrDefault("propertySources", List.of())) {
            Map<String, Object> properties = asMap(asMap(source).get("properties"));
            properties.forEach((key, value) -> {
                if (key.startsWith(prefix) && result.size() < 60) {
                    result.putIfAbsent(key, String.valueOf(asMap(value).get("value")));
                }
            });
        }
        return result;
    }

    @McpTool(
            name = "app_beans",
            description = """
                    Find Spring beans whose name or type contains a substring, with their type and
                    dependencies. Useful for 'is the MCP tool callback provider actually wired'.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public List<String> beans(
            @McpToolParam(description = "Substring to match against bean name or type, e.g. 'Mcp'") String filter) {
        Map<String, Object> body = get("/beans");
        Map<String, Object> contexts = asMap(body.get("contexts"));
        return contexts.values().stream()
                .flatMap(context -> asMap(asMap(context).get("beans")).entrySet().stream())
                .filter(entry -> entry.getKey().toLowerCase().contains(filter.toLowerCase())
                        || String.valueOf(asMap(entry.getValue()).get("type")).contains(filter))
                .map(entry -> entry.getKey() + " : " + asMap(entry.getValue()).get("type"))
                .sorted()
                .limit(40)
                .toList();
    }

    @McpTool(
            name = "app_get_log_level",
            description = "Read the configured and effective log level of one logger.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public LoggerLevel getLogLevel(
            @McpToolParam(description = "Logger name, e.g. 'org.springframework.ai.mcp'") String logger) {
        Map<String, Object> body = get("/loggers/" + logger);
        return new LoggerLevel(
                logger,
                String.valueOf(body.getOrDefault("configuredLevel", "null")),
                String.valueOf(body.getOrDefault("effectiveLevel", "null")));
    }

    @McpTool(
            name = "app_set_log_level",
            description = """
                    Change a logger's level at runtime. Only loggers under the configured allow-list may be
                    changed. This is the one tool here that mutates the running system, so the host will ask
                    the user to confirm it.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public LoggerLevel setLogLevel(
            @McpToolParam(description = "Logger name") String logger,
            @McpToolParam(description = "TRACE, DEBUG, INFO, WARN, ERROR or OFF") String level) {
        if (!properties.writeEnabled()) {
            throw new IllegalStateException("Log level changes are disabled on this server.");
        }
        boolean allowed = properties.writableLoggers().stream().anyMatch(logger::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Logger '%s' is not in the writable allow-list %s".formatted(logger, properties.writableLoggers()));
        }
        client.post()
                .uri("/loggers/{logger}", logger)
                .body(Map.of("configuredLevel", level.toUpperCase(java.util.Locale.ROOT)))
                .retrieve()
                .toBodilessEntity();
        log.info("Agent changed log level: {} -> {}", logger, level);
        return getLogLevel(logger);
    }

    @McpTool(
            name = "app_thread_summary",
            description = """
                    Summarise the JVM thread dump: total threads, counts per state, and the busiest stack
                    frames. Returns a summary, never the raw dump - a full thread dump is tens of thousands
                    of tokens and will not help you.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, openWorldHint = false))
    public ThreadSummary threadSummary() {
        Map<String, Object> body = get("/threaddump");
        List<?> threads = (List<?>) body.getOrDefault("threads", List.of());

        Map<String, Long> byState = threads.stream()
                .map(ActuatorTools::asMap)
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> String.valueOf(t.getOrDefault("threadState", "UNKNOWN")),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));

        List<String> topStacks = threads.stream()
                .map(ActuatorTools::asMap)
                .map(t -> (List<?>) t.getOrDefault("stackTrace", List.of()))
                .filter(stack -> !stack.isEmpty())
                .map(stack -> asMap(stack.getFirst()))
                .map(frame -> frame.get("className") + "." + frame.get("methodName"))
                .collect(java.util.stream.Collectors.groupingBy(s -> s, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> e.getKey() + " x" + e.getValue())
                .toList();

        return new ThreadSummary(threads.size(), byState, topStacks);
    }

    private Map<String, Object> get(String path) {
        return Optional.ofNullable(client.get().uri(path).retrieve().body(OBJECT)).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
