package com.slack.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private Long id;
    private Long channelId;
    private Long userId;
    private String content;
    private Long parentMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String timestampId;  // Timestamp-based message ID (unique per channel)
}

