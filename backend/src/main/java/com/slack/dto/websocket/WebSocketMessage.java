package com.slack.dto.websocket;

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

    public enum MessageType {
        MESSAGE,      // 일반 메시지
        JOIN,         // 채널 참여
        LEAVE,        // 채널 나가기
        ERROR         // 에러
    }
}

