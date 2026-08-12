package com.dataart.jc.agent.telemetry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * Records what actually happened during one turn so the UI can show it.
 *
 * <p>Half of what makes an agent demo convincing is showing the audience the tool calls. Half of
 * what makes an agent debuggable in production is the same thing, with a trace id attached.
 */
@Component
public class TurnTrace {

    public record Entry(String tool, String arguments, String result, long millis, String status) {
    }

    private final Map<String, List<Entry>> byConversation = new ConcurrentHashMap<>();

    public void start(String conversation) {
        byConversation.put(conversation, new CopyOnWriteArrayList<>());
    }

    public void record(String conversation, Entry entry) {
        byConversation.computeIfAbsent(conversation, key -> new CopyOnWriteArrayList<>()).add(entry);
    }

    public List<Entry> drain(String conversation) {
        List<Entry> entries = byConversation.remove(conversation);
        return entries == null ? List.of() : List.copyOf(entries);
    }
}
