package com.slack.service.auth;

import org.springframework.security.core.Authentication;

/**
 * Authentication 객체에서 authUserId를 추출하는 인터페이스
 * 프로덕션과 개발 모드에서 서로 다른 구현체를 사용합니다.
 */
public interface AuthenticationExtractor {

    /**
     * Authentication 객체에서 authUserId를 추출합니다.
     *
     * @param authentication Spring Security Authentication 객체
     * @return authUserId (username), 추출 실패 시 null
     */
    String extractAuthUserId(Authentication authentication);
}
