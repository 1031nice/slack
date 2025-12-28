package com.slack.user.repository;

import com.slack.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAuthUserId(String authUserId);

    Optional<User> findByEmail(String email);

    /**
     * Find users by name (case-insensitive)
     * Used for @mention detection
     *
     * @param name User name (case-insensitive)
     * @return List of users with matching name
     */
    List<User> findByNameIgnoreCase(String name);

    /**
     * Find users by names (case-insensitive, batch query)
     * Prevents N+1 query problem when processing multiple @mentions
     *
     * @param names Collection of user names (case-insensitive)
     * @return List of users with matching names
     */
    List<User> findByNameIgnoreCaseIn(java.util.Collection<String> names);
}

