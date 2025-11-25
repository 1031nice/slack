package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.dto.websocket.WebSocketMessage;
import com.slack.service.MessageService;
import com.slack.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketController 단위 테스트")
class WebSocketControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private UserService userService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private WebSocketController webSocketController;

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
    void handleMessage_Success() {
        // given
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("auth-123");
        when(userService.findByAuthUserId("auth-123")).thenReturn(testUser);
        when(messageService.createMessage(anyLong(), any(MessageCreateRequest.class)))
                .thenReturn(testMessageResponse);

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        // UserService 호출 확인
        verify(userService, times(1)).findByAuthUserId("auth-123");

        // MessageService 호출 확인
        ArgumentCaptor<MessageCreateRequest> requestCaptor = ArgumentCaptor.forClass(MessageCreateRequest.class);
        verify(messageService, times(1)).createMessage(eq(1L), requestCaptor.capture());
        
        MessageCreateRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getUserId()).isEqualTo(1L);
        assertThat(capturedRequest.getContent()).isEqualTo("Test message");

        // 브로드캐스팅 확인
        ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/channel.1"),
                messageCaptor.capture()
        );

        WebSocketMessage broadcastedMessage = messageCaptor.getValue();
        assertThat(broadcastedMessage.getType()).isEqualTo(WebSocketMessage.MessageType.MESSAGE);
        assertThat(broadcastedMessage.getChannelId()).isEqualTo(1L);
        assertThat(broadcastedMessage.getMessageId()).isEqualTo(100L);
        assertThat(broadcastedMessage.getUserId()).isEqualTo(1L);
        assertThat(broadcastedMessage.getContent()).isEqualTo("Test message");
    }

    @Test
    @DisplayName("인증 정보가 없으면 에러 메시지를 전송한다")
    void handleMessage_NoAuthentication() {
        // given
        when(authentication.getPrincipal()).thenReturn(null);
        when(authentication.getName()).thenReturn(null);

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        verify(userService, never()).findByAuthUserId(anyString());
        verify(messageService, never()).createMessage(anyLong(), any());

        // 에러 메시지 전송 확인 (authentication이 null이 아니지만 getName()이 null인 경우)
        ArgumentCaptor<WebSocketMessage> errorCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/queue/errors.null"),
                errorCaptor.capture()
        );

        WebSocketMessage errorMessage = errorCaptor.getValue();
        assertThat(errorMessage.getType()).isEqualTo(WebSocketMessage.MessageType.ERROR);
        assertThat(errorMessage.getContent()).contains("Failed to send message");
    }

    @Test
    @DisplayName("JWT가 아닌 Principal이면 에러 메시지를 전송한다")
    void handleMessage_InvalidPrincipal() {
        // given
        when(authentication.getPrincipal()).thenReturn("invalid-principal");
        when(authentication.getName()).thenReturn("user-123");

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        verify(userService, never()).findByAuthUserId(anyString());
        verify(messageService, never()).createMessage(anyLong(), any());

        // 에러 메시지 전송 확인
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/queue/errors.user-123"),
                any(WebSocketMessage.class)
        );
    }

    @Test
    @DisplayName("메시지 생성 실패 시 에러 메시지를 전송한다")
    void handleMessage_MessageCreationFails() {
        // given
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.getName()).thenReturn("auth-123");
        when(jwt.getSubject()).thenReturn("auth-123");
        when(userService.findByAuthUserId("auth-123")).thenReturn(testUser);
        when(messageService.createMessage(anyLong(), any(MessageCreateRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        verify(userService, times(1)).findByAuthUserId("auth-123");
        verify(messageService, times(1)).createMessage(anyLong(), any());

        // 에러 메시지 전송 확인
        ArgumentCaptor<WebSocketMessage> errorCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/queue/errors.auth-123"),
                errorCaptor.capture()
        );

        WebSocketMessage errorMessage = errorCaptor.getValue();
        assertThat(errorMessage.getType()).isEqualTo(WebSocketMessage.MessageType.ERROR);
        assertThat(errorMessage.getContent()).contains("Failed to send message");
        assertThat(errorMessage.getContent()).contains("Database error");
    }

    @Test
    @DisplayName("User를 찾지 못하면 에러 메시지를 전송한다")
    void handleMessage_UserNotFound() {
        // given
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.getName()).thenReturn("auth-999");
        when(jwt.getSubject()).thenReturn("auth-999");
        when(userService.findByAuthUserId("auth-999"))
                .thenThrow(new RuntimeException("User not found"));

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        verify(userService, times(1)).findByAuthUserId("auth-999");
        verify(messageService, never()).createMessage(anyLong(), any());

        // 에러 메시지 전송 확인
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/queue/errors.auth-999"),
                any(WebSocketMessage.class)
        );
    }
}

