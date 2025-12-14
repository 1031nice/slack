package com.slack.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.slack.dto.auth.LoginResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RestTemplate restTemplate;
    @Value("${auth.server.url:http://localhost:8081}")
    private String authServerUrl;
    @Value("${auth.client.id:slack}")
    private String clientId;
    @Value("${auth.client.secret:slack-secret-key}")
    private String clientSecret;

    /**
     * Exchanges authorization code for access token (OAuth2 Authorization Code Flow).
     */
    public LoginResponse exchangeToken(String code, String redirectUri) {
        try {
            HttpEntity<MultiValueMap<String, String>> request = buildTokenRequest(code, redirectUri);
            AuthServerResponse authResponse = executeTokenExchange(request);
            return parseTokenResponse(authResponse);
        } catch (HttpClientErrorException e) {
            return handleHttpClientError(e);
        } catch (ResourceAccessException e) {
            return handleNetworkError(e);
        } catch (Exception e) {
            log.error("Error during token exchange", e);
            throw new RuntimeException("Authentication failed: " + e.getMessage());
        }
    }

    /**
     * Builds HTTP request entity for token exchange.
     */
    private HttpEntity<MultiValueMap<String, String>> buildTokenRequest(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        return new HttpEntity<>(body, headers);
    }

    /**
     * Executes token exchange request to auth server.
     */
    private AuthServerResponse executeTokenExchange(HttpEntity<MultiValueMap<String, String>> request) {
        String tokenEndpoint = authServerUrl + "/oauth2/token";

        ResponseEntity<AuthServerResponse> response = restTemplate.exchange(
                tokenEndpoint,
                HttpMethod.POST,
                request,
                AuthServerResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to get token from auth server");
        }

        return response.getBody();
    }

    /**
     * Parses auth server response into LoginResponse.
     */
    private LoginResponse parseTokenResponse(AuthServerResponse authResponse) {
        return new LoginResponse(
                authResponse.getAccessToken(),
                authResponse.getTokenType(),
                authResponse.getExpiresIn()
        );
    }

    /**
     * Handles HTTP client errors from auth server.
     */
    private LoginResponse handleHttpClientError(HttpClientErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        log.error("Auth server error: status={}, body={}, message={}",
                e.getStatusCode(), responseBody, e.getMessage());

        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
            throw new RuntimeException("Invalid authorization code: " +
                    (responseBody != null ? responseBody : e.getMessage()));
        }

        throw new RuntimeException("Authentication failed: " +
                (responseBody != null ? responseBody : e.getMessage()));
    }

    /**
     * Handles network errors when connecting to auth server.
     */
    private LoginResponse handleNetworkError(ResourceAccessException e) {
        log.error("Network error connecting to auth server: {}", e.getMessage());
        throw new RuntimeException("Cannot connect to authentication server: " + e.getMessage());
    }

    // OAuth2 standard token response format (snake_case)
    @Getter
    @Setter
    private static class AuthServerResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private Long expiresIn;
    }
}

