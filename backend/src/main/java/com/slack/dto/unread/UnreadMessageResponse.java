package com.slack.dto.unread;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadMessageResponse {
    private Long messageId;
    private Long channelId;
    private String channelName;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
    private Long sequenceNumber;
    private String timestampId;  // Timestamp-based message ID (unique per channel)
}

