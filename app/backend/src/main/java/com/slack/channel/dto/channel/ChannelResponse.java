package com.slack.channel.dto;

import com.slack.channel.domain.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResponse {
    private Long id;
    private Long workspaceId;
    private String name;
    private ChannelType type;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long unreadCount; // Number of unread messages in this channel
}

