package com.slack.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개발용 간단 로그인 요청
 * username만 입력하면 토큰을 발급합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DevLoginRequest {

    @NotBlank(message = "Username is required")
    private String username;
}
