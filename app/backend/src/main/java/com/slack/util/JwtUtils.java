package com.slack.util;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility class for extracting user information from JWT tokens
 */
public class JwtUtils {

    private JwtUtils() {
    }

    /**
     * Extract authentication user ID (sub claim) from JWT
     */
    public static String extractAuthUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null) {
            return subject;
        }
        return jwt.getClaimAsString("sub");
    }

    /**
     * Extract user info from JWT
     */
    public static UserInfo extractUserInfo(Jwt jwt) {
        String authUserId = extractAuthUserId(jwt);
        String email = extractEmail(jwt);
        String name = extractName(jwt, email);
        
        return new UserInfo(authUserId, email, name);
    }

    private static String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return email;
    }

    private static String extractName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (name == null) {
            if (email != null && email.contains("@")) {
                name = email.split("@")[0];
            } else {
                name = "User";
            }
        }
        return name;
    }

    public record UserInfo(String authUserId, String email, String name) {
    }
}

