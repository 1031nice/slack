package com.slack.auth.controller;

import com.slack.auth.dto.DevLoginRequest;
import com.slack.auth.dto.LoginResponse;
import com.slack.auth.dto.TokenExchangeRequest;
import com.slack.auth.service.AuthService;
import com.slack.util.DevJwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.slack.common.controller.ResponseHelper.ok;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired(required = false)
    private DevJwtUtil devJwtUtil;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/token")
    public ResponseEntity<LoginResponse> exchangeToken(@Valid @RequestBody TokenExchangeRequest request) {
        LoginResponse response = authService.exchangeToken(request.getCode(), request.getRedirectUri());
        return ok(response);
    }

    /**
     * 개발용 간단 로그인 엔드포인트
     * dev 프로파일에서만 활성화됩니다.
     *
     * @param request username
     * @return JWT 토큰
     */
    @PostMapping("/dev/login")
    @Profile("dev")
    public ResponseEntity<LoginResponse> devLogin(@Valid @RequestBody DevLoginRequest request) {
        String token = devJwtUtil.generateToken(request.getUsername());

        LoginResponse response = LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24 hours
                .build();

        return ok(response);
    }
}

