package com.slack.config;

import com.slack.user.domain.User;
import com.slack.user.service.UserService;
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
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // TODO: Restrict origins in production
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticateConnect(accessor);
                }

                return message;
            }
        });
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authToken = accessor.getFirstNativeHeader("Authorization");
        if (authToken == null || !authToken.startsWith("Bearer ")) {
            log.warn("No Authorization header in CONNECT message");
            return;
        }

        try {
            String token = authToken.substring(7);
            Authentication authentication = authenticate(token);
            if (authentication != null) {
                accessor.setUser(authentication);
            }
        } catch (Exception e) {
            log.error("JWT authentication failed during CONNECT", e);
        }
    }

    private Authentication authenticate(String token) {
        // Dev mode: use DevJwtUtil
        if (devJwtUtil != null && devJwtUtil.validateToken(token)) {
            return authenticateDevMode(token);
        }

        // Production mode: use OAuth2 JwtDecoder
        return authenticateProductionMode(token);
    }

    private Authentication authenticateDevMode(String token) {
        String authUserId = devJwtUtil.extractUsername(token);
        User user = userService.findOrCreateDevUser(authUserId);

        log.debug("WebSocket dev authentication successful: {}", authUserId);
        return new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
    }

    private Authentication authenticateProductionMode(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        String authUserId = jwt.getSubject();
        User user = userService.findByAuthUserId(authUserId);

        log.debug("WebSocket OAuth2 authentication successful: {}", authUserId);
        return new UsernamePasswordAuthenticationToken(user, jwt, Collections.emptyList());
    }
}

