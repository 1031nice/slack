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
 * 개발용 간단한 JWT 유틸리티
 * 실제 프로덕션에서는 OAuth2 서버를 사용해야 합니다.
 */
@Component
@Profile("dev")
public class DevJwtUtil {

    // 개발용 시크릿 키 (32바이트 이상)
    private static final String SECRET = "dev-secret-key-for-local-development-only-do-not-use-in-production";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long EXPIRATION_HOURS = 24;

    /**
     * 개발용 JWT 토큰 생성
     *
     * @param username 사용자 이름
     * @return JWT 토큰
     */
    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(username)
                .claim("sub", username)  // OAuth2 표준 claim
                .claim("username", username)
                .claim("email", username + "@dev.local")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(KEY)
                .compact();
    }

    /**
     * JWT 토큰에서 username 추출
     *
     * @param token JWT 토큰
     * @return username
     */
    public String extractUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * JWT 토큰 검증
     *
     * @param token JWT 토큰
     * @return 유효 여부
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
