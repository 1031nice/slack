package com.slack.controller;

import com.slack.dto.websocket.WebSocketMessage;
import com.slack.service.WebSocketMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketController 단위 테스트")
class WebSocketControllerTest {

    @Mock
    private WebSocketMessageService webSocketMessageService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WebSocketController webSocketController;

    private WebSocketMessage testWebSocketMessage;

    @BeforeEach
    void setUp() {
        // Test WebSocketMessage
        testWebSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(1L)
                .content("Test message")
                .build();
    }

    @Test
    @DisplayName("메시지를 받아서 서비스에 위임한다")
    void handleMessage_Success() {
        // given
        when(webSocketMessageService.handleIncomingMessage(any(WebSocketMessage.class), any(Authentication.class)))
                .thenReturn(testWebSocketMessage);

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        verify(webSocketMessageService, times(1)).handleIncomingMessage(testWebSocketMessage, authentication);
        verify(webSocketMessageService, never()).sendErrorMessage(any(), anyString());
    }

    @Test
    @DisplayName("서비스에서 예외가 발생하면 에러 메시지를 전송한다")
    void handleMessage_ServiceThrowsException() {
        // given
        when(webSocketMessageService.handleIncomingMessage(any(WebSocketMessage.class), any(Authentication.class)))
                .thenThrow(new IllegalArgumentException("Authentication required"));

        // when
        webSocketController.handleMessage(testWebSocketMessage, authentication);

        // then
        verify(webSocketMessageService, times(1)).handleIncomingMessage(testWebSocketMessage, authentication);
        verify(webSocketMessageService, times(1)).sendErrorMessage(authentication, "Authentication required");
    }

}

