package com.slack.dto.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka event for read receipt updates
 * Published when a user reads messages in a channel
 *
 * Used for durable persistence via Kafka batching (ADR-0007)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptEvent {

    /**
     * User ID who read the messages
     */
    private Long userId;

    /**
     * Channel ID where messages were read
     */
    private Long channelId;

    /**
     * Last read timestamp (timestampId from v0.5 event-based architecture)
     * Format: {unix_timestamp_μs}.{3-digit-sequence}
     * Example: "1640995200123456.001"
     */
    private String lastReadTimestamp;

    /**
     * Event creation timestamp (for debugging and lag measurement)
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Generate Kafka partition key: userId:channelId
     * Ensures ordering for same user-channel pair
     */
    public String getPartitionKey() {
        return userId + ":" + channelId;
    }
}
