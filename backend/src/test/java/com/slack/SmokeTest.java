package com.slack;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify that the application context loads successfully.
 *
 * This test catches:
 * - Bean creation errors (unsatisfied dependencies, invalid queries, etc.)
 * - Configuration issues
 * - JPA entity/repository validation errors
 *
 * Purpose: Fail fast on application startup issues that would otherwise
 * only be discovered at runtime.
 *
 * NOTE: Disabled by default as it requires infrastructure (Redis, Kafka).
 * Run manually or in CI/CD pipeline with proper infrastructure setup.
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires Redis and Kafka infrastructure - run manually or in integration test suite")
class SmokeTest {

    /**
     * Verify that the Spring application context loads successfully.
     *
     * This simple test ensures:
     * - All beans are created without errors
     * - All JPA repositories have valid queries
     * - All dependencies are satisfied
     * - Configuration is valid
     */
    @Test
    void contextLoads() {
        // If the context loads successfully, the test passes
        // This catches runtime errors like:
        // - UnsatisfiedDependencyException
        // - QueryCreationException
        // - BeanCreationException
    }
}
