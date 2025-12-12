package com.slack.repository;

import com.slack.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAuthUserId(String authUserId);
    
    /**
     * Find users by name (case-insensitive)
     * Used for @mention detection
     * 
     * @param name User name (case-insensitive)
     * @return List of users with matching name
     */
    List<User> findByNameIgnoreCase(String name);
}

