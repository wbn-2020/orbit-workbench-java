package com.orbitworkbench.agent.application;

import java.time.Duration;
import java.util.function.LongSupplier;

final class TextDeltaBuffer {

    private static final int DEFAULT_MAX_CHARACTERS = 256;
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(200);

    private final int maxCharacters;
    private final long maxDelayNanos;
    private final LongSupplier nanoTime;
    private final StringBuilder buffer = new StringBuilder();
    private long firstAppendNanos;

    static TextDeltaBuffer createDefault() {
        return new TextDeltaBuffer(
                DEFAULT_MAX_CHARACTERS,
                DEFAULT_MAX_DELAY,
                System::nanoTime);
    }

    TextDeltaBuffer(int maxCharacters,
                    Duration maxDelay,
                    LongSupplier nanoTime) {
        this.maxCharacters = maxCharacters;
        this.maxDelayNanos = maxDelay.toNanos();
        this.nanoTime = nanoTime;
    }

    boolean append(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (buffer.isEmpty()) {
            firstAppendNanos = nanoTime.getAsLong();
        }
        buffer.append(text);
        return buffer.length() >= maxCharacters
                || nanoTime.getAsLong() - firstAppendNanos >= maxDelayNanos;
    }

    boolean hasContent() {
        return !buffer.isEmpty();
    }

    String drain() {
        String text = buffer.toString();
        buffer.setLength(0);
        firstAppendNanos = 0L;
        return text;
    }
}
