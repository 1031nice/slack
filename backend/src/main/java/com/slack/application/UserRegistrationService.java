package com.slack.application;

import com.slack.domain.user.User;
import com.slack.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Application service for user registration.
 * Creates users without auto-assigning workspaces.
 * Users join workspaces via invitation or by creating their own.
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserService userService;

    /**
     * Finds an existing user by authUserId or creates a new user.
     * New users start with zero workspaces and must join via invitation or creation.
     */
    public User findOrCreateUser(String authUserId, String email, String name) {
        return userService.findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> userService.createUser(authUserId, email, name));
    }
}
