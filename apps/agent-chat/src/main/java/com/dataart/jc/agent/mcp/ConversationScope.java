package com.dataart.jc.agent.mcp;

import java.util.concurrent.Callable;

/**
 * Carries the conversation id from the HTTP request down into a tool callback without threading it
 * through six method signatures.
 *
 * <p>Uses {@code ScopedValue} (finalised in Java 25): immutable, automatically unbound at the end of
 * the block, and - unlike {@code ThreadLocal} - safe to inherit into structured-concurrency forks.
 * This is exactly the kind of "invisible plumbing" a ThreadLocal used to leak.
 */
public final class ConversationScope {

    private static final ScopedValue<String> CONVERSATION = ScopedValue.newInstance();

    private ConversationScope() {
    }

    public static <T> T with(String conversationId, Callable<T> action) throws Exception {
        return ScopedValue.where(CONVERSATION, conversationId).call(action);
    }

    public static String current() {
        return CONVERSATION.isBound() ? CONVERSATION.get() : "default";
    }
}
