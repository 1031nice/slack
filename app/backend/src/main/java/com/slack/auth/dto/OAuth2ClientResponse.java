package com.slack.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ClientResponse {
    private Long id;
    
    @JsonProperty("clientId")
    private String clientId;
    
    @JsonProperty("redirectUris")
    private List<String> redirectUris;
    
    @JsonProperty("scopes")
    private List<String> scopes;
    
    @JsonProperty("grantTypes")
    private List<String> grantTypes;
    
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

