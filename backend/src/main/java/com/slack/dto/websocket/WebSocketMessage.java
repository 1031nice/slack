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
    private Long sequenceNumber;  // 메시지 순서 보장 및 ACK를 위한 시퀀스 번호
    private String timestampId;    // Timestamp-based message ID (unique per channel)
    private String ackId;          // ACK 메시지 ID (ACK 타입일 때 사용)

    public enum MessageType {
        MESSAGE,      // 일반 메시지
        JOIN,         // 채널 참여
        LEAVE,        // 채널 나가기
        ERROR,        // 에러
        ACK,          // 메시지 수신 확인
        RESEND,       // 메시지 재전송 요청
        MENTION,      // @mention 알림
        READ          // 읽음 처리 (read receipt)
    }
}

