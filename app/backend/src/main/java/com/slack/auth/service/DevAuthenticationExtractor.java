package com.slack.auth.service;

import com.slack.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 개발 모드용 AuthenticationExtractor 구현체
 * Principal이 User 객체인 경우를 처리합니다.
 */
@Slf4j
@Component
@Profile("dev")
public class DevAuthenticationExtractor implements AuthenticationExtractor {

    @Override
    public String extractAuthUserId(Authentication authentication) {
        if (authentication == null) {
            log.warn("Authentication is null");
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            User user = (User) principal;
            return user.getAuthUserId();
        }

        log.warn("Principal is not a User object: {}", principal.getClass().getName());
        return null;
    }
}
