package com.slack.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessage {
    private MessageType type;
    private Long channelId;
    private Long messageId;
    private Long userId;
    private String content;
    private String createdAt;
    private String timestampId;    // Timestamp-based message ID (unique per channel)

    public enum MessageType {
        MESSAGE,
        JOIN,
        LEAVE,
        ERROR,
        RESEND,
        MENTION,
        READ
    }
}

