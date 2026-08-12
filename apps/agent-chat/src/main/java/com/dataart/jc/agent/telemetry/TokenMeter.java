package com.dataart.jc.agent.telemetry;

/**
 * Deliberately crude token estimator.
 *
 * <p>No tokenizer dependency: for English prose and JSON, characters / 3.7 lands within a few
 * percent of a BPE count, which is precision enough to make the point on a slide. If you need the
 * real number in production, use the tokenizer that ships with your model.
 */
public final class TokenMeter {

    private static final double CHARS_PER_TOKEN = 3.7;

    private TokenMeter() {
    }

    public static int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
