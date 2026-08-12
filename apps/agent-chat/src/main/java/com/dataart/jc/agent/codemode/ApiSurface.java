package com.dataart.jc.agent.codemode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns MCP tool definitions into something a language model has actually seen a billion examples
 * of: a typed API.
 *
 * <p>This is the whole trick behind "Code Mode". A tool definition is JSON Schema wrapped in a
 * protocol envelope. A function signature is code. Models are far better at the second one, because
 * that is what their training data is made of.
 */
public final class ApiSurface {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

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
                         * %s%s
                         */
                        declare function %s(args: %s): any;"""
                        .formatted(
                                definition.description().replace("\n", "\n * "),
                                parameterDocs(definition.inputSchema()),
                                definition.name(),
                                typeLiteral(definition.inputSchema())))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * The per-parameter descriptions, as {@code @param} lines.
     *
     * <p>Easy to leave out, and expensive when you do. A JSON Schema carries a description on every
     * property; a bare TypeScript signature carries none. Drop them and the model is left guessing at
     * arguments it cannot derive - which is how you get a confident call against a repository that
     * does not exist. The type literal says <em>what shape</em>; only these lines say <em>what value</em>.
     */
    private static String parameterDocs(String inputSchema) {
        Object properties = schema(inputSchema).get("properties");
        if (!(properties instanceof Map<?, ?> map) || map.isEmpty()) {
            return "";
        }
        String docs = map.entrySet().stream()
                .map(entry -> {
                    Object description = entry.getValue() instanceof Map<?, ?> property
                            ? property.get("description") : null;
                    String text = description == null ? "" : String.valueOf(description);
                    return text.isBlank()
                            ? null
                            : " * @param %s %s".formatted(entry.getKey(), text.replace("\n", " ").strip());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return docs.isEmpty() ? "" : "\n *\n" + docs;
    }

    private static Map<String, Object> schema(String inputSchema) {
        try {
            return JSON.readValue(inputSchema, MAP_TYPE);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** Names for the one-line index, with a trailing {@code ?} on anything the model may omit. */
    private static List<String> parameterNames(String inputSchema) {
        Map<String, Object> root = schema(inputSchema);
        Object properties = root.get("properties");
        if (!(properties instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<String> required = requiredNames(root);
        return map.keySet().stream()
                .map(String::valueOf)
                .map(name -> required.contains(name) ? name : name + "?")
                .toList();
    }

    private static List<String> requiredNames(Map<String, Object> root) {
        return root.get("required") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static String typeLiteral(String inputSchema) {
        Map<String, Object> root = schema(inputSchema);
        Object properties = root.get("properties");
        if (!(properties instanceof Map<?, ?> map) || map.isEmpty()) {
            return "{}";
        }
        List<String> required = requiredNames(root);

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
