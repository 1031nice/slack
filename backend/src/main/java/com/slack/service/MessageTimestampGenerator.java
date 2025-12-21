package com.slack.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Generates timestamp-based message IDs.
 * Format: {unix_timestamp_ms}.{6-digit-sequence}
 * Example: "1640995200123.000001"
 *
 * - Unique per channel (not globally unique)
 * - Chronologically sortable
 * - Human-readable timestamp component
 */
@Slf4j
@Service
public class MessageTimestampGenerator {

    private long lastTimestamp = -1L;
    private long sequence = 0L;
    private static final long MAX_SEQUENCE = 999999L; // 6 digits: 000000-999999

    /**
     * Generate a timestamp-based message ID.
     * Thread-safe implementation handles same-millisecond collisions with sequence counter.
     *
     * @return timestamp ID in format "{timestamp_ms}.{sequence:06d}"
     */
    public synchronized String generateTimestampId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp == lastTimestamp) {
            // Same millisecond - increment sequence
            sequence++;
            if (sequence > MAX_SEQUENCE) {
                // Sequence overflow - wait for next millisecond
                log.warn("Sequence overflow at timestamp {}. Waiting for next millisecond.", currentTimestamp);
                currentTimestamp = waitForNextMillisecond(currentTimestamp);
                sequence = 0L;
            }
        } else if (currentTimestamp > lastTimestamp) {
            // New millisecond - reset sequence
            sequence = 0L;
            lastTimestamp = currentTimestamp;
        } else {
            // Clock moved backwards - wait until it catches up
            log.warn("Clock moved backwards. Current: {}, Last: {}. Waiting...", currentTimestamp, lastTimestamp);
            currentTimestamp = waitForNextMillisecond(lastTimestamp);
            sequence = 0L;
            lastTimestamp = currentTimestamp;
        }

        return formatTimestampId(currentTimestamp, sequence);
    }

    /**
     * Format timestamp and sequence into Slack-style ID.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @param sequence Sequence number (0-999999)
     * @return formatted ID: "{timestamp}.{sequence:06d}"
     */
    private String formatTimestampId(long timestamp, long sequence) {
        return String.format("%d.%06d", timestamp, sequence);
    }

    /**
     * Wait until the next millisecond.
     *
     * @param currentTimestamp current timestamp to wait past
     * @return next millisecond timestamp
     */
    private long waitForNextMillisecond(long currentTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= currentTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
