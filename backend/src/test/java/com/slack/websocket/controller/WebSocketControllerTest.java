package com.slack.websocket.controller;

import com.slack.user.domain.User;
import com.slack.websocket.dto.WebSocketMessage;
import com.slack.websocket.service.WebSocketMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketController 단위 테스트")
class WebSocketControllerTest {

    @Mock
    private WebSocketMessageService webSocketMessageService;

    @InjectMocks
    private WebSocketController webSocketController;

    private WebSocketMessage testWebSocketMessage;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        // Test User
        testUser = User.builder()
                .authUserId("auth-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        setField(testUser, "id", 1L);

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
    @DisplayName("메시지를 받아서 서비스에 위임한다")
    void handleMessage_Success() {
        // given
        when(webSocketMessageService.handleIncomingMessage(any(WebSocketMessage.class), anyString()))
                .thenReturn(testWebSocketMessage);

        // when
        webSocketController.handleMessage(testWebSocketMessage, testUser);

        // then
        verify(webSocketMessageService, times(1)).handleIncomingMessage(testWebSocketMessage, "auth-123");
        verify(webSocketMessageService, never()).sendErrorMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("서비스에서 예외가 발생하면 에러 메시지를 전송한다")
    void handleMessage_ServiceThrowsException() {
        // given
        when(webSocketMessageService.handleIncomingMessage(any(WebSocketMessage.class), anyString()))
                .thenThrow(new IllegalArgumentException("User not found"));

        // when
        webSocketController.handleMessage(testWebSocketMessage, testUser);

        // then
        verify(webSocketMessageService, times(1)).handleIncomingMessage(testWebSocketMessage, "auth-123");
        verify(webSocketMessageService, times(1)).sendErrorMessage("auth-123", "User not found");
    }

}

