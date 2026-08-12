package com.dataart.jc.agent.codemode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.json.JsonParser;

/**
 * Turns MCP tool definitions into something a language model has actually seen a billion examples
 * of: a typed API.
 *
 * <p>This is the whole trick behind "Code Mode". A tool definition is JSON Schema wrapped in a
 * protocol envelope. A function signature is code. Models are far better at the second one, because
 * that is what their training data is made of.
 */
public final class ApiSurface {

    private ApiSurface() {
    }

    /** One line per tool: cheap enough to always keep in context. */
    public static String index(List<ToolCallback> tools, String filter) {
        return tools.stream()
                .map(ToolCallback::getToolDefinition)
                .filter(definition -> filter == null || filter.isBlank()
                        || definition.name().contains(filter)
                        || definition.description().toLowerCase().contains(filter.toLowerCase()))
                .map(definition -> "%s(%s) - %s".formatted(
                        definition.name(),
                        String.join(", ", parameterNames(definition.inputSchema())),
                        firstSentence(definition.description())))
                .collect(Collectors.joining("\n"));
    }

    /** Full TypeScript-style declarations, loaded only for the tools the model asked about. */
    public static String declarations(List<ToolCallback> tools) {
        return tools.stream()
                .map(ToolCallback::getToolDefinition)
                .map(definition -> """
                        /**
                         * %s
                         */
                        declare function %s(args: %s): any;"""
                        .formatted(
                                definition.description().replace("\n", "\n * "),
                                definition.name(),
                                typeLiteral(definition.inputSchema())))
                .collect(Collectors.joining("\n\n"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String inputSchema) {
        try {
            return JsonParser.fromJson(inputSchema, Map.class);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parameterNames(String inputSchema) {
        Object properties = schema(inputSchema).get("properties");
        return properties instanceof Map<?, ?> map
                ? map.keySet().stream().map(String::valueOf).toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static String typeLiteral(String inputSchema) {
        Map<String, Object> root = schema(inputSchema);
        Object properties = root.get("properties");
        if (!(properties instanceof Map<?, ?> map) || map.isEmpty()) {
            return "{}";
        }
        List<String> required = root.get("required") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();

        String fields = map.entrySet().stream()
                .map(entry -> {
                    String name = String.valueOf(entry.getKey());
                    Map<String, Object> property = entry.getValue() instanceof Map<?, ?> m
                            ? (Map<String, Object>) m : Map.of();
                    String type = switch (String.valueOf(property.getOrDefault("type", "string"))) {
                        case "integer", "number" -> "number";
                        case "boolean" -> "boolean";
                        case "array" -> "string[]";
                        case "object" -> "Record<string, unknown>";
                        default -> "string";
                    };
                    return "%s%s: %s".formatted(name, required.contains(name) ? "" : "?", type);
                })
                .collect(Collectors.joining("; "));
        return "{ " + fields + " }";
    }

    private static String firstSentence(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String flat = description.replace('\n', ' ').replaceAll("\\s+", " ").strip();
        int dot = flat.indexOf(". ");
        return dot > 0 ? flat.substring(0, dot + 1) : flat;
    }
}
