package com.slack.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.slack.dto.auth.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${auth.server.url:http://localhost:8081}")
    private String authServerUrl;

    @Value("${auth.client.id:slack}")
    private String clientId;

    @Value("${auth.client.secret:slack-secret-key}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    /**
     * Authorization code를 토큰으로 교환합니다 (OAuth2 Authorization Code Flow).
     */
    public LoginResponse exchangeToken(String code, String redirectUri) {
        try {
            String tokenEndpoint = authServerUrl + "/oauth2/token";
            
            // OAuth2 Authorization Code Flow
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", code);
            body.add("redirect_uri", redirectUri);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<AuthServerResponse> response = restTemplate.exchange(
                    tokenEndpoint,
                    HttpMethod.POST,
                    entity,
                    AuthServerResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AuthServerResponse authResponse = response.getBody();
                return new LoginResponse(
                        authResponse.getAccessToken(),
                        authResponse.getTokenType(),
                        authResponse.getExpiresIn()
                );
            }

            throw new RuntimeException("Failed to get token from auth server");
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Auth server error: status={}, body={}, message={}", 
                    e.getStatusCode(), responseBody, e.getMessage());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new RuntimeException("Invalid authorization code: " + (responseBody != null ? responseBody : e.getMessage()));
            }
            throw new RuntimeException("Authentication failed: " + (responseBody != null ? responseBody : e.getMessage()));
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Network error connecting to auth server: {}", e.getMessage());
            throw new RuntimeException("Cannot connect to authentication server: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error during token exchange", e);
            throw new RuntimeException("Authentication failed: " + e.getMessage());
        }
    }

    // 인증 서버 응답을 위한 내부 클래스
    // OAuth2 표준 응답 형식: access_token, token_type, expires_in (snake_case)
    private static class AuthServerResponse {
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("token_type")
        private String tokenType;
        
        @JsonProperty("expires_in")
        private Long expiresIn;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public Long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
        }
    }
}

