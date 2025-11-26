package com.slack.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenExchangeRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;
    
    @NotBlank(message = "Redirect URI is required")
    private String redirectUri;
}

