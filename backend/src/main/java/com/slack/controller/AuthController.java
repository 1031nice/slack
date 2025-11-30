package com.slack.controller;

import com.slack.dto.auth.LoginResponse;
import com.slack.dto.auth.TokenExchangeRequest;
import com.slack.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.slack.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token")
    public ResponseEntity<LoginResponse> exchangeToken(@Valid @RequestBody TokenExchangeRequest request) {
        LoginResponse response = authService.exchangeToken(request.getCode(), request.getRedirectUri());
        return ok(response);
    }
}

