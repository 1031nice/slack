package com.slack.unread.controller;

import com.slack.user.domain.User;
import com.slack.unread.dto.UnreadViewResponse;
import com.slack.unread.service.UnreadViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.slack.common.controller.ResponseHelper.ok;

/**
 * REST controller for unread view operations.
 * Provides aggregated view of all unread messages across channels.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UnreadController {

    private final UnreadViewService unreadViewService;

    /**
     * Get aggregated unread messages across all channels for the authenticated user.
     *
     * @param sort Sort option: "newest" (default), "oldest", or "channel"
     * @param limit Maximum number of messages to return (default: 50, max: 200)
     * @param user Authenticated user
     * @return UnreadViewResponse with sorted unread messages
     */
    @GetMapping("/unreads")
    public ResponseEntity<UnreadViewResponse> getUnreads(
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal User user) {
        UnreadViewResponse response = unreadViewService.getUnreads(user.getId(), sort, limit);
        return ok(response);
    }
}

