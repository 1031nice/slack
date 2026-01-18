package com.slack.presence.controller;

import com.slack.presence.service.PresenceService;
import com.slack.user.domain.User;
import com.slack.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * API for user presence status and heartbeats.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final UserService userService;

    /**
     * Updates the heartbeat for the authenticated user.
     * In a production WebSocket environment, this would ideally be handled
     * via WebSocket frame interceptors, but REST is provided here for flexibility.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal Jwt jwt) {
        User user = userService.findByAuthUserId(jwt.getSubject());
        presenceService.updateHeartbeat(user.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * Checks online status for a list of user IDs.
     * Useful for rendering the "green dot" in the UI.
     */
    @GetMapping("/status")
    public ResponseEntity<Set<Long>> getOnlineStatus(@RequestParam List<Long> userIds) {
        Set<Long> onlineUserIds = presenceService.getOnlineUsers(userIds);
        return ResponseEntity.ok(onlineUserIds);
    }
}
