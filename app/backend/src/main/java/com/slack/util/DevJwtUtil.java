package com.slack.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Simple JWT utility for development mode only
 * Production should use OAuth2 server
 */
@Component
@Profile("dev")
public class DevJwtUtil {

    private static final String SECRET = "dev-secret-key-for-local-development-only-do-not-use-in-production";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long EXPIRATION_HOURS = 24;

    /**
     * Generate dev JWT token
     *
     * @param username username
     * @return JWT token
     */
    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(username)
                .claim("sub", username)
                .claim("username", username)
                .claim("email", username + "@dev.local")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(KEY)
                .compact();
    }

    /**
     * Extract username from JWT token
     * In dev mode, treats plain text as userId (no validation)
     *
     * @param token JWT token or plain text userId
     * @return username
     */
    public String extractUsername(String token) {
        try {
            // Try to parse as JWT first (for backward compatibility)
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            // If not a valid JWT, treat the token itself as userId
            return token;
        }
    }

    /**
     * Validate JWT token
     * In dev mode, always returns true
     *
     * @param token JWT token or plain text userId
     * @return always true in dev mode
     */
    public boolean validateToken(String token) {
        return true;
    }
}
