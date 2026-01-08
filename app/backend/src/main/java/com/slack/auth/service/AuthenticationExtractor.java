package com.slack.auth.service;

import org.springframework.security.core.Authentication;

/**
 * Interface for extracting authUserId from Authentication
 * Different implementations for production and dev modes
 */
public interface AuthenticationExtractor {

    /**
     * Extract authUserId from Authentication
     *
     * @param authentication Spring Security Authentication object
     * @return authUserId, or null if extraction fails
     */
    String extractAuthUserId(Authentication authentication);
}
