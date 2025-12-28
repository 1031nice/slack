package com.slack.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final Map<String, String> HOME_RESPONSE = Map.of(
            "message", "Slack App Backend is running!",
            "status", "ok"
    );

    private static final Map<String, String> HEALTH_RESPONSE = Map.of(
            "status", "UP"
    );

    @GetMapping("/")
    public Map<String, String> home() {
        return HOME_RESPONSE;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return HEALTH_RESPONSE;
    }
}

