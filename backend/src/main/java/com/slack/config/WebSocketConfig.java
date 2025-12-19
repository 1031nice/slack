package com.slack.config;

import com.slack.domain.user.User;
import com.slack.service.UserService;
import com.slack.util.DevJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserService userService;

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Autowired(required = false)
    private DevJwtUtil devJwtUtil;

    public WebSocketConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 브로커 설정
        // 클라이언트가 구독할 수 있는 destination prefix
        config.enableSimpleBroker("/topic", "/queue");
        // 클라이언트가 메시지를 보낼 때 사용할 destination prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 엔드포인트 등록
        // 클라이언트는 이 엔드포인트로 연결
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // CORS 설정 (개발용, 프로덕션에서는 특정 도메인 지정)
                .withSockJS(); // SockJS 지원 (fallback 옵션)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    // CONNECT 메시지: 초기 인증
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // CONNECT 명령: Authorization 헤더에서 JWT 추출 및 인증 설정
                    String authToken = accessor.getFirstNativeHeader("Authorization");
                    if (authToken != null && authToken.startsWith("Bearer ")) {
                        try {
                            String token = authToken.substring(7);
                            Authentication authentication = null;

                            // Dev 모드: DevJwtUtil 사용
                            if (devJwtUtil != null && devJwtUtil.validateToken(token)) {
                                String authUserId = devJwtUtil.extractUsername(token);

                                // Find or create user
                                User user = userService.findByAuthUserIdOptional(authUserId)
                                        .orElseGet(() -> {
                                            String email = authUserId + "@dev.local";
                                            return userService.createUser(authUserId, email, authUserId);
                                        });

                                authentication = new UsernamePasswordAuthenticationToken(
                                        user, null, Collections.emptyList());

                                log.debug("WebSocket dev authentication successful: {}", authUserId);
                            }
                            // Production 모드: OAuth2 JwtDecoder 사용
                            else {
                                Jwt jwt = jwtDecoder.decode(token);
                                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                                authentication = converter.convert(jwt);

                                log.debug("WebSocket OAuth2 authentication successful: {}", authentication.getName());
                            }

                            if (authentication != null) {
                                // ✅ 핵심: setUser()로 Principal 설정
                                accessor.setUser(authentication);
                            }
                        } catch (Exception e) {
                            log.error("JWT authentication failed during CONNECT", e);
                            // 인증 실패 시 연결 거부하려면 예외를 throw
                            // throw new AuthenticationException("Invalid JWT token");
                        }
                    } else {
                        log.warn("No Authorization header in CONNECT message");
                    }
                    }
                    // SEND, SUBSCRIBE 등 다른 메시지: 세션에서 인증 정보 복원
                    else if (accessor.getUser() != null) {
                        // 세션에 저장된 User를 Authentication으로 변환
                        if (accessor.getUser() instanceof Authentication) {
                            // 이미 Authentication 객체인 경우 그대로 사용
                            accessor.setUser(accessor.getUser());
                        }
                    }
                }

                return message;
            }
        });
    }
}

