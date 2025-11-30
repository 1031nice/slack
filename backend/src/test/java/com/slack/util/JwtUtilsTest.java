package com.slack.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JwtUtils 단위 테스트")
class JwtUtilsTest {

    @Test
    @DisplayName("JWT에서 subject로 인증 사용자 ID를 추출할 수 있다")
    void extractAuthUserId_WithSubject() {
        // given
        Jwt jwt = createJwt(Map.of("sub", "user-123"), "user-123");

        // when
        String result = JwtUtils.extractAuthUserId(jwt);

        // then
        assertThat(result).isEqualTo("user-123");
    }

    @Test
    @DisplayName("JWT에서 subject가 null이면 sub 클레임을 사용한다")
    void extractAuthUserId_WithSubClaim() {
        // given
        Jwt jwt = createJwt(Map.of("sub", "user-456"), null);

        // when
        String result = JwtUtils.extractAuthUserId(jwt);

        // then
        assertThat(result).isEqualTo("user-456");
    }

    @Test
    @DisplayName("JWT에서 모든 정보가 있을 때 사용자 정보를 추출할 수 있다")
    void extractUserInfo_WithAllClaims() {
        // given
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "email", "test@example.com",
                "name", "Test User"
        ), "user-123");

        // when
        JwtUtils.UserInfo result = JwtUtils.extractUserInfo(jwt);

        // then
        assertThat(result.authUserId()).isEqualTo("user-123");
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.name()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("JWT에서 email이 없으면 preferred_username을 사용한다")
    void extractUserInfo_WithPreferredUsername() {
        // given
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "preferred_username", "username@example.com",
                "name", "Test User"
        ), "user-123");

        // when
        JwtUtils.UserInfo result = JwtUtils.extractUserInfo(jwt);

        // then
        assertThat(result.authUserId()).isEqualTo("user-123");
        assertThat(result.email()).isEqualTo("username@example.com");
        assertThat(result.name()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("JWT에서 name이 없으면 email의 @ 앞부분을 사용한다")
    void extractUserInfo_NameFromEmail() {
        // given
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "email", "john.doe@example.com"
        ), "user-123");

        // when
        JwtUtils.UserInfo result = JwtUtils.extractUserInfo(jwt);

        // then
        assertThat(result.authUserId()).isEqualTo("user-123");
        assertThat(result.email()).isEqualTo("john.doe@example.com");
        assertThat(result.name()).isEqualTo("john.doe");
    }

    @Test
    @DisplayName("JWT에서 name과 email이 없으면 기본값 'User'를 사용한다")
    void extractUserInfo_DefaultName() {
        // given
        Jwt jwt = createJwt(Map.of("sub", "user-123"), "user-123");

        // when
        JwtUtils.UserInfo result = JwtUtils.extractUserInfo(jwt);

        // then
        assertThat(result.authUserId()).isEqualTo("user-123");
        assertThat(result.email()).isNull();
        assertThat(result.name()).isEqualTo("User");
    }

    @Test
    @DisplayName("JWT에서 preferred_username이 있고 name이 없으면 preferred_username에서 이름을 추출한다")
    void extractUserInfo_NameFromPreferredUsername() {
        // given
        Jwt jwt = createJwt(Map.of(
                "sub", "user-123",
                "preferred_username", "alice@example.com"
        ), "user-123");

        // when
        JwtUtils.UserInfo result = JwtUtils.extractUserInfo(jwt);

        // then
        assertThat(result.authUserId()).isEqualTo("user-123");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.name()).isEqualTo("alice");
    }

    private Jwt createJwt(Map<String, Object> claims, String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("sub")).thenReturn((String) claims.get("sub"));
        when(jwt.getClaimAsString("email")).thenReturn((String) claims.get("email"));
        when(jwt.getClaimAsString("preferred_username")).thenReturn((String) claims.get("preferred_username"));
        when(jwt.getClaimAsString("name")).thenReturn((String) claims.get("name"));
        return jwt;
    }
}

