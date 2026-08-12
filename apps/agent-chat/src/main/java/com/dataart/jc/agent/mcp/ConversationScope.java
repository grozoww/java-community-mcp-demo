package com.dataart.jc.agent.mcp;

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

    /**
     * Note the operation type: the finalised Java 25 API takes {@code ScopedValue.CallableOp}, not
     * {@code Callable}. It is generic over the thrown type, so a body that throws nothing stays
     * exception-free at the call site instead of being forced into {@code throws Exception}.
     */
    public static <T, X extends Throwable> T with(String conversationId,
                                                  ScopedValue.CallableOp<T, X> action) throws X {
        return ScopedValue.where(CONVERSATION, conversationId).call(action);
    }

    public static String current() {
        return CONVERSATION.isBound() ? CONVERSATION.get() : "default";
    }
}
