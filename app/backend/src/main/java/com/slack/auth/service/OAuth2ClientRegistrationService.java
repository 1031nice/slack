package com.slack.auth.service;

import com.slack.auth.dto.OAuth2ClientRequest;
import com.slack.auth.dto.OAuth2ClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * DEV/TEST ONLY - Auto-registers OAuth2 client on startup.
 */
@Slf4j
@Service
@Profile("!prod")
@RequiredArgsConstructor
public class OAuth2ClientRegistrationService implements ApplicationRunner {

    private final RestTemplate restTemplate;
    @Value("${auth.server.url:http://localhost:8081}")
    private String authServerUrl;
    @Value("${auth.client.id:slack}")
    private String clientId;
    @Value("${auth.client.secret:slack-secret-key}")
    private String clientSecret;
    @Value("${auth.client.redirect-uris:http://localhost:3000/auth/callback,http://localhost:3000/signup/callback,http://localhost:3000/callback}")
    private String redirectUris;
    @Value("${auth.client.auto-register:false}")
    private boolean autoRegister;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoRegister) {
            log.info("OAuth2 client auto-registration is disabled. Set 'auth.client.auto-register=true' to enable.");
            log.info("Please register the client manually: Client ID='{}', Secret='{}'", clientId, clientSecret);
            return;
        }

        log.info("Starting OAuth2 client registration...");
        log.info("Auth server URL: {}", authServerUrl);
        log.info("Client ID: {}", clientId);
        try {
            registerOAuth2Client();
        } catch (Exception e) {
            log.warn("Failed to register OAuth2 client on startup: {}", e.getMessage());
            log.info("This is not critical - the backend will continue to run.");
            log.info("Please register the client manually at {}/api/v1/oauth2/clients", authServerUrl);
            log.info("Required client info: ID='{}', Secret='{}', Grant Types=[authorization_code, refresh_token, client_credentials]",
                    clientId, clientSecret);
        }
    }

    private void registerOAuth2Client() {
        String registrationEndpoint = authServerUrl + "/api/v1/oauth2/clients";
        log.info("Attempting to register OAuth2 client at: {}", registrationEndpoint);

        OAuth2ClientRequest request = OAuth2ClientRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUris(parseRedirectUris())
                .scopes(Arrays.asList("read", "write"))
                .grantTypes(Arrays.asList("authorization_code", "refresh_token", "client_credentials"))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OAuth2ClientRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<OAuth2ClientResponse> response = restTemplate.exchange(
                    registrationEndpoint,
                    HttpMethod.POST,
                    entity,
                    OAuth2ClientResponse.class
            );

            log.info("Registration response status: {}", response.getStatusCode());
            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("OAuth2 client '{}' registered successfully", clientId);
            } else if (response.getStatusCode().is3xxRedirection()) {
                log.warn("Received redirect response ({}). The registration endpoint requires authentication.", response.getStatusCode());
                log.warn("Location header: {}", response.getHeaders().getLocation());
                log.warn("Auto-registration failed: The endpoint requires authentication, but the API spec doesn't specify security requirements.");
                throw new RuntimeException("Registration endpoint requires authentication or returned redirect");
            } else {
                log.warn("Unexpected response status: {}", response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("HTTP error during registration: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.info("OAuth2 client '{}' may already be registered (status: {})", clientId, e.getStatusCode());
            } else if (e.getStatusCode() == HttpStatus.FOUND || e.getStatusCode().is3xxRedirection()) {
                log.warn("Received redirect response ({}). The registration endpoint may require authentication.", e.getStatusCode());
                throw new RuntimeException("Registration endpoint requires authentication or returned redirect");
            } else {
                throw e;
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Network error during registration: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OAuth2 client registration", e);
            throw e;
        }
    }

    private List<String> parseRedirectUris() {
        if (redirectUris == null || redirectUris.isEmpty()) {
            return Arrays.asList("http://localhost:3000/auth/callback");
        }
        return Arrays.asList(redirectUris.split(","));
    }
}

