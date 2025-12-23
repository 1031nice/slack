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
    private String timestampId;    // Timestamp-based message ID (unique per channel)

    public enum MessageType {
        MESSAGE,      // 일반 메시지
        JOIN,         // 채널 참여
        LEAVE,        // 채널 나가기
        ERROR,        // 에러
        RESEND,       // 메시지 재전송 요청
        MENTION,      // @mention 알림
        READ          // 읽음 처리 (read receipt)
    }
}

