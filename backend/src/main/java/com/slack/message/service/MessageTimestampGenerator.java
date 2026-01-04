package com.slack.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Generates timestamp-based message IDs (Slack-style).
 *
 * Format: "{unix_timestamp_ms}.{3-digit-sequence}"
 * Example: "1736000000000.001"
 *
 * Architecture: Channel Partitioning with Consistent Hashing
 * - Each channel is handled by exactly ONE server (via Nginx hash routing)
 * - That server assigns timestamps sequentially
 * - No clock skew issues (single authority per channel)
 * - Perfect ordering within channel (100% guaranteed)
 *
 * This matches Slack's production architecture:
 * "The channel server arbitrated message order - when two users hit send
 * simultaneously, the channel server decided which message came first"
 *
 * @see <a href="https://blog.bytebytego.com/p/how-slack-supports-billions-of-daily">
 *      How Slack Supports Billions of Daily Messages</a>
 */
@Slf4j
@Service
public class MessageTimestampGenerator {

    private long lastMilliseconds = -1L;
    private int sequence = 0;
    private static final int MAX_SEQUENCE = 999; // 3 digits: 000-999

    /**
     * Generate a timestamp-based message ID (Slack-style).
     *
     * Thread-safe implementation handles same-millisecond messages with sequence counter.
     * Since channel partitioning guarantees single server per channel, no coordination needed.
     *
     * @return timestamp ID in format "{timestamp_ms}.{sequence:03d}"
     *         ASCII sortable, just like Slack's message IDs
     */
    public synchronized String generateTimestampId() {
        long currentMillis = System.currentTimeMillis(); // Wall clock, not nanoTime!

        if (currentMillis == lastMilliseconds) {
            // Same millisecond - increment sequence
            sequence++;
            if (sequence > MAX_SEQUENCE) {
                // Sequence overflow - wait for next millisecond
                log.warn("Sequence overflow at millisecond {}. Waiting for next millisecond.", currentMillis);
                currentMillis = waitForNextMillisecond(currentMillis);
                sequence = 0;
            }
        } else if (currentMillis > lastMilliseconds) {
            // New millisecond - reset sequence
            sequence = 0;
            lastMilliseconds = currentMillis;
        } else {
            // Clock moved backwards - wait until it catches up
            log.warn("Clock moved backwards. Current: {}, Last: {}. Waiting...", currentMillis, lastMilliseconds);
            currentMillis = waitForNextMillisecond(lastMilliseconds);
            sequence = 0;
            lastMilliseconds = currentMillis;
        }

        return formatTimestampId(currentMillis, sequence);
    }

    /**
     * Format timestamp and sequence into Slack-style message ID.
     *
     * Slack's API documentation:
     * "All messages within a single channel are guaranteed to have a unique
     * timestamp which is ASCII sortable"
     *
     * @param milliseconds Unix timestamp in milliseconds
     * @param sequence Sequence number (0-999)
     * @return formatted ID: "{milliseconds}.{sequence:03d}"
     */
    private String formatTimestampId(long milliseconds, int sequence) {
        return String.format("%d.%03d", milliseconds, sequence);
    }

    /**
     * Wait until the next millisecond.
     *
     * @param currentMillis current millisecond timestamp to wait past
     * @return next millisecond timestamp
     */
    private long waitForNextMillisecond(long currentMillis) {
        long millis = System.currentTimeMillis();
        while (millis <= currentMillis) {
            millis = System.currentTimeMillis();
        }
        return millis;
    }
}
