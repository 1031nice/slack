package com.slack.user.controller;

import com.slack.user.domain.User;
import com.slack.user.dto.UserResponse;
import com.slack.user.service.UserService;
import com.slack.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import static com.slack.common.controller.ResponseHelper.created;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Register current authenticated user in the system.
     * This should be called once after OAuth authentication to create user record.
     *
     * Note: Uses @AuthenticationPrincipal Jwt (not User) because user doesn't exist yet.
     * JwtToUserAuthenticationConverter would fail with UserNotFoundException.
     */
    @PostMapping("/me")
    public ResponseEntity<UserResponse> registerUser(@AuthenticationPrincipal Jwt jwt) {
        JwtUtils.UserInfo userInfo = JwtUtils.extractUserInfo(jwt);
        User user = userService.findOrCreateUser(
                userInfo.authUserId(),
                userInfo.email() != null ? userInfo.email() : userInfo.authUserId(),
                userInfo.name()
        );

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .authUserId(user.getAuthUserId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .build();

        return created(response);
    }
}
