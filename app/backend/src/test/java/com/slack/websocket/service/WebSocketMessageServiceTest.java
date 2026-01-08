package com.slack.websocket.service;

import com.slack.user.domain.User;
import com.slack.message.dto.MessageCreateRequest;
import com.slack.message.dto.MessageResponse;
import com.slack.websocket.dto.WebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.slack.message.service.MessageService;
import com.slack.user.service.UserService;
import com.slack.common.service.RedisMessagePublisher;
import com.slack.readreceipt.service.ReadReceiptService;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketMessageService 단위 테스트")
class WebSocketMessageServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private UserService userService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RedisMessagePublisher redisMessagePublisher;

    @Mock
    private ReadReceiptService readReceiptService;

    @Mock
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private WebSocketMessageService webSocketMessageService;

    private User testUser;
    private MessageResponse testMessageResponse;
    private WebSocketMessage testWebSocketMessage;

    @BeforeEach
    void setUp() throws Exception {
        // Test User
        testUser = User.builder()
                .authUserId("auth-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        setField(testUser, "id", 1L);

        // Test MessageResponse
        testMessageResponse = MessageResponse.builder()
                .id(100L)
                .channelId(1L)
                .userId(1L)
                .content("Test message")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Test WebSocketMessage
        testWebSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(1L)
                .content("Test message")
                .build();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("메시지를 받아서 처리하고 브로드캐스팅한다")
    void handleIncomingMessage_Success() {
        // given
        when(userService.findByAuthUserId("auth-123")).thenReturn(testUser);
        when(messageService.createMessage(anyLong(), any(MessageCreateRequest.class)))
                .thenReturn(testMessageResponse);

        // when
        WebSocketMessage result = webSocketMessageService.handleIncomingMessage(testWebSocketMessage, "auth-123");

        // then
        verify(userService, times(1)).findByAuthUserId("auth-123");

        ArgumentCaptor<MessageCreateRequest> requestCaptor = ArgumentCaptor.forClass(MessageCreateRequest.class);
        verify(messageService, times(1)).createMessage(eq(1L), requestCaptor.capture());

        MessageCreateRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getUserId()).isEqualTo(1L);
        assertThat(capturedRequest.getContent()).isEqualTo("Test message");

        ArgumentCaptor<WebSocketMessage> redisCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(redisMessagePublisher, times(1)).publish(redisCaptor.capture());

        WebSocketMessage broadcastedMessage = redisCaptor.getValue();
        assertThat(broadcastedMessage.getType()).isEqualTo(WebSocketMessage.MessageType.MESSAGE);
        assertThat(broadcastedMessage.getChannelId()).isEqualTo(1L);
        assertThat(broadcastedMessage.getMessageId()).isEqualTo(100L);
        assertThat(broadcastedMessage.getUserId()).isEqualTo(1L);
        assertThat(broadcastedMessage.getContent()).isEqualTo("Test message");

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(WebSocketMessage.MessageType.MESSAGE);
        assertThat(result.getChannelId()).isEqualTo(1L);
        assertThat(result.getMessageId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("User를 찾지 못하면 예외가 발생한다")
    void handleIncomingMessage_UserNotFound() {
        // given
        when(userService.findByAuthUserId("auth-999"))
                .thenThrow(new RuntimeException("User not found"));

        // when & then
        assertThatThrownBy(() -> webSocketMessageService.handleIncomingMessage(testWebSocketMessage, "auth-999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");

        verify(userService, times(1)).findByAuthUserId("auth-999");
        verify(messageService, never()).createMessage(anyLong(), any());
    }

    @Test
    @DisplayName("메시지 생성 실패 시 예외가 발생한다")
    void handleIncomingMessage_MessageCreationFails() {
        // given
        when(userService.findByAuthUserId("auth-123")).thenReturn(testUser);
        when(messageService.createMessage(anyLong(), any(MessageCreateRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // when & then
        assertThatThrownBy(() -> webSocketMessageService.handleIncomingMessage(testWebSocketMessage, "auth-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");

        verify(userService, times(1)).findByAuthUserId("auth-123");
        verify(messageService, times(1)).createMessage(anyLong(), any(MessageCreateRequest.class));
        verify(redisMessagePublisher, never()).publish(any(WebSocketMessage.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(WebSocketMessage.class));
    }

    @Test
    @DisplayName("에러 메시지를 특정 사용자에게 전송한다")
    void sendErrorMessage_WithUsername() {
        // when
        webSocketMessageService.sendErrorMessage("auth-123", "Test error");

        // then
        ArgumentCaptor<WebSocketMessage> errorCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/queue/errors.auth-123"),
                errorCaptor.capture()
        );

        WebSocketMessage errorMessage = errorCaptor.getValue();
        assertThat(errorMessage.getType()).isEqualTo(WebSocketMessage.MessageType.ERROR);
        assertThat(errorMessage.getContent()).isEqualTo("Failed to send message: Test error");
    }

    @Test
    @DisplayName("메시지를 특정 채널의 모든 구독자에게 브로드캐스팅한다")
    void broadcastToChannel_Success() {
        // given
        WebSocketMessage message = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(1L)
                .content("Broadcast message")
                .build();

        // when
        webSocketMessageService.broadcastToChannel(message);

        // then
        ArgumentCaptor<WebSocketMessage> redisCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(redisMessagePublisher, times(1)).publish(redisCaptor.capture());
        
        WebSocketMessage publishedMessage = redisCaptor.getValue();
        assertThat(publishedMessage.getType()).isEqualTo(WebSocketMessage.MessageType.MESSAGE);
        assertThat(publishedMessage.getChannelId()).isEqualTo(1L);
        assertThat(publishedMessage.getContent()).isEqualTo("Broadcast message");
    }
}

