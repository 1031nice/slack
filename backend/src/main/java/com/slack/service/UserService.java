package com.slack.service;

import com.slack.domain.user.User;
import com.slack.exception.UserNotFoundException;
import com.slack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * User domain service handling user-related business logic.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * Finds user by authUserId or creates a new one if not found.
     * This is the primary method for OAuth-based user registration.
     */
    @Transactional
    public User findOrCreateUser(String authUserId, String email, String name) {
        return findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> createUser(authUserId, email, name));
    }

    /**
     * Finds user by authUserId, throws exception if not found.
     */
    public User findByAuthUserId(String authUserId) {
        return userRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found with authUserId: " + authUserId));
    }

    /**
     * Finds user by authUserId, returns Optional.
     */
    public Optional<User> findByAuthUserIdOptional(String authUserId) {
        return userRepository.findByAuthUserId(authUserId);
    }

    /**
     * Finds user by ID, throws exception if not found.
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    /**
     * Creates a new user.
     */
    @Transactional
    public User createUser(String authUserId, String email, String name) {
        User newUser = User.builder()
                .authUserId(authUserId)
                .email(email)
                .name(name)
                .build();
        return userRepository.save(newUser);
    }

    /**
     * Finds or creates a dev mode user with @dev.local email.
     * Used for development authentication without OAuth.
     */
    @Transactional
    public User findOrCreateDevUser(String authUserId) {
        return findByAuthUserIdOptional(authUserId)
                .orElseGet(() -> createUser(authUserId, authUserId + "@dev.local", authUserId));
    }
}

