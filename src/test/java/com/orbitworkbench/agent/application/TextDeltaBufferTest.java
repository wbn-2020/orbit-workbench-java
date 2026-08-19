package com.orbitworkbench.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TextDeltaBufferTest {

    @Test
    void flushesWhenCharacterLimitIsReached() {
        TextDeltaBuffer buffer = new TextDeltaBuffer(
                5, Duration.ofSeconds(1), () -> 0L);

        assertFalse(buffer.append("ab"));
        assertTrue(buffer.append("cde"));
        assertEquals("abcde", buffer.drain());
        assertFalse(buffer.hasContent());
    }

    @Test
    void flushesWhenTimeWindowExpires() {
        AtomicLong now = new AtomicLong();
        TextDeltaBuffer buffer = new TextDeltaBuffer(
                100, Duration.ofMillis(200), now::get);

        assertFalse(buffer.append("first"));
        now.set(Duration.ofMillis(250).toNanos());
        assertTrue(buffer.append(" second"));
        assertEquals("first second", buffer.drain());
    }

    @Test
    void preservesWhitespaceAndCanFlushFinalPartialChunk() {
        TextDeltaBuffer buffer = new TextDeltaBuffer(
                100, Duration.ofSeconds(1), () -> 0L);

        assertFalse(buffer.append("hello"));
        assertFalse(buffer.append("\nworld"));
        assertTrue(buffer.hasContent());
        assertEquals("hello\nworld", buffer.drain());
    }
}
