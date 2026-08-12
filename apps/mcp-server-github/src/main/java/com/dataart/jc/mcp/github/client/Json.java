package com.dataart.jc.mcp.github.client;

import java.util.List;
import java.util.Map;

/** Tiny null-safe accessors so the tool code stays readable. */
public final class Json {

    private Json() {
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String str(Map<String, Object> map, String key, String fallback) {
        String value = str(map, key);
        return value == null ? fallback : value;
    }

    public static int i32(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public static long i64(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public static boolean bool(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Boolean flag && flag;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Map<?, ?> nested ? (Map<String, Object>) nested : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> arr(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /** Keeps free-text fields (issue bodies, comments) from blowing up the context window. */
    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String normalised = text.replace("\r\n", "\n").strip();
        return normalised.length() <= max ? normalised : normalised.substring(0, max) + "\n...[clipped]";
    }
}
