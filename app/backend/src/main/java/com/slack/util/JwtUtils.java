package com.slack.util;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT 토큰에서 사용자 정보를 추출하는 유틸리티 클래스
 */
public class JwtUtils {

    private JwtUtils() {
        // 유틸리티 클래스이므로 인스턴스화 방지
    }

    /**
     * JWT에서 인증 사용자 ID(sub 클레임)를 추출합니다.
     * 
     * @param jwt JWT 토큰
     * @return 인증 사용자 ID
     */
    public static String extractAuthUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null) {
            return subject;
        }
        // fallback: sub 클레임이 없으면 sub 클레임을 직접 조회
        return jwt.getClaimAsString("sub");
    }

    /**
     * JWT에서 사용자 정보를 추출합니다.
     * 
     * @param jwt JWT 토큰
     * @return 사용자 정보 (authUserId, email, name)
     */
    public static UserInfo extractUserInfo(Jwt jwt) {
        String authUserId = extractAuthUserId(jwt);
        String email = extractEmail(jwt);
        String name = extractName(jwt, email);
        
        return new UserInfo(authUserId, email, name);
    }

    /**
     * JWT에서 이메일을 추출합니다.
     * email 클레임이 없으면 preferred_username을 사용합니다.
     */
    private static String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return email;
    }

    /**
     * JWT에서 이름을 추출합니다.
     * name 클레임이 없으면 이메일의 @ 앞부분을 사용하거나 "User"를 기본값으로 사용합니다.
     */
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

    /**
     * JWT에서 추출한 사용자 정보를 담는 record
     */
    public record UserInfo(String authUserId, String email, String name) {
    }
}

