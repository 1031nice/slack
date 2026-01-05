package com.slack.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ClientRequest {
    private String clientId;
    private String clientSecret;
    private List<String> redirectUris;
    private List<String> scopes;
    private List<String> grantTypes;
}

