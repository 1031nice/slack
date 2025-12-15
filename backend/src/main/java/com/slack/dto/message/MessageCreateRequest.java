package com.slack.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal DTO for message creation.
 * This is NOT exposed to external clients via REST API.
 * Used internally by WebSocketMessageService to create messages with server-generated
 * userId and sequenceNumber.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCreateRequest {

    /**
     * User ID (server-generated from authentication)
     */
    @NotNull(message = "User ID is required")
    private Long userId;

    /**
     * Message content
     */
    @NotBlank(message = "Message content is required")
    @Size(min = 1, message = "Message content must not be empty")
    private String content;

    /**
     * Sequence number for message ordering (server-generated via Redis INCR)
     */
    private Long sequenceNumber;
}

