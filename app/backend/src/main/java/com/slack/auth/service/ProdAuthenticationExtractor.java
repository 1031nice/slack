package com.slack.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 프로덕션 모드용 AuthenticationExtractor 구현체
 * Principal이 OAuth2 Jwt 객체인 경우를 처리합니다.
 */
@Slf4j
@Component
@Profile("!dev")
public class ProdAuthenticationExtractor implements AuthenticationExtractor {

    @Override
    public String extractAuthUserId(Authentication authentication) {
        if (authentication == null) {
            log.warn("Authentication is null");
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt) {
            Jwt jwt = (Jwt) principal;
            return jwt.getSubject();
        }

        log.warn("Principal is not a Jwt object: {}", principal.getClass().getName());
        return null;
    }
}
